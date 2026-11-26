package io.github.slavikjunior.syncclip.core.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * NSD-дискавери Android через [NsdManager] (FR-2.1).
 *
 * `resolveService` на части версий Android не допускает параллельных вызовов,
 * поэтому найденные сервисы резолвятся по очереди: `onServiceFound` только
 * добавляет в очередь, а следующий резолв запускается после предыдущего.
 */
internal class AndroidNsdLanDiscovery(
    advertisement: LanAdvertisement,
    context: Context,
) : LanDiscovery {

    private val manager = context.applicationContext
        .getSystemService(Context.NSD_SERVICE) as NsdManager
    private val advertisement = advertisement

    private val state = MutableStateFlow<List<LanPeer>>(emptyList())
    override val peers: StateFlow<List<LanPeer>> = state.asStateFlow()

    private val found = mutableMapOf<String, LanPeer>()
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null

    override suspend fun start() = withContext(Dispatchers.Main) {
        if (discovery != null) return@withContext

        val info = NsdServiceInfo().apply {
            serviceName = advertisement.deviceId
            serviceType = SERVICE_TYPE
            port = advertisement.port
            setAttribute("userId", advertisement.userId)
            setAttribute("deviceId", advertisement.deviceId)
        }
        val registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        registration = registrationListener

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                synchronized(queue) {
                    queue.addLast(serviceInfo)
                    if (!resolving) resolveNext()
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(found) {
                    val removed = found.remove(serviceInfo.serviceName) != null
                    if (removed) state.value = found.values.toList()
                }
            }
        }
        manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        discovery = discoveryListener
    }

    override suspend fun stop() = withContext(Dispatchers.Main) {
        discovery?.let { runCatching { manager.stopServiceDiscovery(it) } }
        registration?.let { runCatching { manager.unregisterService(it) } }
        discovery = null
        registration = null
        synchronized(queue) {
            queue.clear()
            resolving = false
        }
        synchronized(found) {
            found.clear()
            state.value = emptyList()
        }
    }

    private fun resolveNext() {
        val next = synchronized(queue) { queue.pollFirst() }
        if (next == null) {
            resolving = false
            return
        }
        resolving = true
        manager.resolveService(
            next,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    synchronized(queue) { resolving = false; resolveNextLocked() }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    onResolved(serviceInfo)
                    synchronized(queue) { resolving = false; resolveNextLocked() }
                }
            },
        )
    }

    private fun resolveNextLocked() {
        if (queue.isNotEmpty() && !resolving) resolveNext()
    }

    private fun onResolved(info: NsdServiceInfo) {
        val attributes = info.attributes ?: return
        val userId = attributes["userId"]?.decodeToString() ?: return
        val deviceId = attributes["deviceId"]?.decodeToString() ?: info.serviceName
        if (userId != advertisement.userId) return
        if (deviceId == advertisement.deviceId) return
        val host = info.host?.hostAddress ?: return

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

    private companion object {
        const val SERVICE_TYPE = "_syncclip._tcp"
    }
}
