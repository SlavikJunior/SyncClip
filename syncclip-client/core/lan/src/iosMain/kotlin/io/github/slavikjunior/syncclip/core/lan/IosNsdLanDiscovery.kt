package io.github.slavikjunior.syncclip.core.lan

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readValue
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSData
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.darwin.NSObject
import platform.Foundation.create
import platform.posix.AF_INET
import platform.darwin.inet_ntoa
import platform.posix.memcpy
import platform.posix.sockaddr_in

/**
 * NSD-дискавери iOS через Bonjour (`NSNetService`/`NSNetServiceBrowser`).
 *
 * Публикует `_syncclip._tcp.` с TXT `userId`/`deviceId`, слушает анонсы и
 * резолвит первый IPv4-адрес. Пиры другого аккаунта игнорируются.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosNsdLanDiscovery(
    private val advertisement: LanAdvertisement,
) : LanDiscovery {

    private val state = MutableStateFlow<List<LanPeer>>(emptyList())
    override val peers: StateFlow<List<LanPeer>> = state.asStateFlow()

    private var browser: NSNetServiceBrowser? = null
    private var published: NSNetService? = null
    private var browserDelegate: BrowserDelegate? = null
    private val found = mutableMapOf<String, LanPeer>()

    override suspend fun start() {
        if (browser != null) return
        val delegate = BrowserDelegate(
            onFound = { service -> resolve(service) },
            onResolved = { service -> onResolved(service) },
            onRemoved = { name -> remove(name) },
        )
        browserDelegate = delegate

        val service = NSNetService(
            domain = ".",
            type = SERVICE_TYPE,
            name = advertisement.deviceId,
            port = advertisement.port,
        )
        service.setTXTRecordData(
            NSNetService.dataFromTXTRecordDictionary(
                mapOf(
                    "userId" to advertisement.userId.encodeToByteArray().toNSData(),
                    "deviceId" to advertisement.deviceId.encodeToByteArray().toNSData(),
                ),
            ),
        )
        service.publish()
        published = service

        val serviceBrowser = NSNetServiceBrowser()
        serviceBrowser.delegate = delegate
        serviceBrowser.searchForServicesOfType(SERVICE_TYPE, inDomain = "local.")
        browser = serviceBrowser
    }

    override suspend fun stop() {
        browser?.stop()
        browser = null
        published?.stop()
        published = null
        browserDelegate = null
        found.clear()
        state.value = emptyList()
    }

    private fun resolve(service: NSNetService) {
        // Делегата держим до колбэка: локальная переменная будет собрана.
        service.delegate = browserDelegate
        service.resolveWithTimeout(RESOLVE_TIMEOUT_SECONDS)
    }

    private fun onResolved(service: NSNetService) {
        val userId = service.txtValue("userId") ?: return
        val deviceId = service.txtValue("deviceId") ?: service.name
        if (userId != advertisement.userId) return
        if (deviceId == advertisement.deviceId) return
        val host = service.firstIpv4() ?: return

        found[deviceId] = LanPeer(
            deviceId = deviceId,
            userId = userId,
            host = host,
            port = service.port.toInt(),
        )
        state.value = found.values.toList()
    }

    private fun remove(serviceName: String) {
        val removed = found.entries.removeAll { (_, peer) -> peer.deviceId == serviceName }
        if (removed) state.value = found.values.toList()
    }

    private fun NSNetService.txtValue(key: String): String? {
        val record = TXTRecordData() ?: return null
        val dictionary = NSNetService.dictionaryFromTXTRecordData(record)
        val value = dictionary?.get(key) as? NSData ?: return null
        return value.toByteArray().decodeToString()
    }

    private fun NSNetService.firstIpv4(): String? {
        val list = addresses ?: return null
        for (item in list) {
            val data = item as? NSData ?: continue
            val bytes = data.toByteArray()
            if (bytes.size < sizeOf<sockaddr_in>()) continue
            memScoped {
                val address = alloc<sockaddr_in>()
                bytes.usePinned { pinned ->
                    memcpy(address.ptr, pinned.addressOf(0), sizeOf<sockaddr_in>().toULong())
                }
                if (address.sin_family.toInt() == AF_INET) {
                    return inet_ntoa(address.sin_addr.readValue())?.toKString()
                }
            }
        }
        return null
    }

    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        val result = ByteArray(size)
        if (size == 0) return result
        val source = bytes ?: return result
        result.usePinned { pinned -> memcpy(pinned.addressOf(0), source, length) }
        return result
    }

    private class BrowserDelegate(
        private val onFound: (NSNetService) -> Unit,
        private val onResolved: (NSNetService) -> Unit,
        private val onRemoved: (String) -> Unit,
    ) : NSObject(), NSNetServiceBrowserDelegateProtocol, NSNetServiceDelegateProtocol {

        @ObjCSignatureOverride
        override fun netServiceBrowser(
            browser: NSNetServiceBrowser,
            didFindService: NSNetService,
            moreComing: Boolean,
        ) {
            onFound(didFindService)
        }

        @ObjCSignatureOverride
        override fun netServiceBrowser(
            browser: NSNetServiceBrowser,
            didRemoveService: NSNetService,
            moreComing: Boolean,
        ) {
            onRemoved(didRemoveService.name)
        }

        override fun netServiceDidResolveAddress(sender: NSNetService) {
            onResolved(sender)
        }

        override fun netService(sender: NSNetService, didNotResolve: Map<Any?, *>) {
            // Пир не ответил на резолв — просто не добавляем его.
        }
    }

    private companion object {
        const val SERVICE_TYPE = "_syncclip._tcp."
        const val RESOLVE_TIMEOUT_SECONDS = 5.0
    }
}
