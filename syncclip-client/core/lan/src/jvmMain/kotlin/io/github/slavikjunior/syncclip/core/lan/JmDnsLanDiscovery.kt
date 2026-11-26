package io.github.slavikjunior.syncclip.core.lan

import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * mDNS-дискавери для Desktop через JmDNS (ADR-003).
 *
 * Публикует `_syncclip._tcp.local.` с TXT `userId`/`deviceId`, слушает чужие
 * анонсы и резолвит адрес. Пиры фильтруются по `userId`: чужие аккаунты в той
 * же сети не должны получать буфер.
 */
internal class JmDnsLanDiscovery(
    private val advertisement: LanAdvertisement,
) : LanDiscovery {

    private val state = MutableStateFlow<List<LanPeer>>(emptyList())
    override val peers: StateFlow<List<LanPeer>> = state.asStateFlow()

    private var jmDns: JmDNS? = null
    private var listener: ServiceListener? = null
    private var registered: ServiceInfo? = null
    private val found = mutableMapOf<String, LanPeer>()

    override suspend fun start() = withContext(Dispatchers.IO) {
        if (jmDns != null) return@withContext
        val instance = JmDNS.create()
        jmDns = instance

        val info = ServiceInfo.create(
            SERVICE_TYPE,
            advertisement.deviceId,
            advertisement.port,
            0,
            0,
            mapOf(
                "userId" to advertisement.userId,
                "deviceId" to advertisement.deviceId,
            ),
        )
        instance.registerService(info)
        registered = info

        val serviceListener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                // `true` — ждать полного резолва, иначе hostAddresses пустые.
                instance.requestServiceInfo(event.type, event.name, true)
            }

            override fun serviceResolved(event: ServiceEvent) {
                onResolved(event.info)
            }

            override fun serviceRemoved(event: ServiceEvent) {
                remove(event.name)
            }
        }
        instance.addServiceListener(SERVICE_TYPE, serviceListener)
        listener = serviceListener
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        val instance = jmDns ?: return@withContext
        listener?.let { runCatching { instance.removeServiceListener(SERVICE_TYPE, it) } }
        registered?.let { runCatching { instance.unregisterService(it) } }
        runCatching { instance.close() }
        jmDns = null
        listener = null
        registered = null
        synchronized(found) {
            found.clear()
            state.value = emptyList()
        }
    }

    private fun onResolved(info: ServiceInfo) {
        val userId = info.getPropertyString("userId") ?: return
        val deviceId = info.getPropertyString("deviceId") ?: info.name
        if (userId != advertisement.userId) return
        if (deviceId == advertisement.deviceId) return

        val address = info.inet4Addresses.firstOrNull()
            ?: info.inetAddresses.firstOrNull()
            ?: return
        val host = address.hostAddress ?: return

        synchronized(found) {
            found[deviceId] = LanPeer(
                deviceId = deviceId,
                userId = userId,
                host = host,
                port = info.port,
            )
            state.value = found.values.toList()
        }
    }

    private fun remove(serviceName: String) {
        synchronized(found) {
            val removed = found.entries.removeAll { (_, peer) -> peer.deviceId == serviceName }
            if (removed) state.value = found.values.toList()
        }
    }

    private companion object {
        const val SERVICE_TYPE = "_syncclip._tcp.local."
    }
}
