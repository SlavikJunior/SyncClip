package io.github.slavikjunior.syncclip.feature.sync.impl.data.transport

import io.github.slavikjunior.syncclip.core.clipboard.EpochClock
import io.github.slavikjunior.syncclip.core.lan.LanAdvertisement
import io.github.slavikjunior.syncclip.core.lan.LanDiscovery
import io.github.slavikjunior.syncclip.core.lan.LanDiscoveryFactory
import io.github.slavikjunior.syncclip.core.lan.LanPeer
import io.github.slavikjunior.syncclip.core.lan.createLanDialer
import io.github.slavikjunior.syncclip.core.lan.createLanServer
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeClipboardSettingsStore
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeDeviceIdentityStore
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeIncomingSink
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeKeyProvider
import io.github.slavikjunior.syncclip.feature.sync.impl.FakePacketSource
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeSignedInSession
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncPacket
import io.github.slavikjunior.syncclip.shared.crypto.AesGcmPayloadCipher
import io.github.slavikjunior.syncclip.shared.crypto.secureRandomBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Два LAN-транспорта на loopback: настоящие TCP-сервер и клиент.
 *
 * Проверяет то, что не видно на фейках: поднятие сервера на случайном порту,
 * кадрирование, шифрование и ACK за 300 мс без участия сервера.
 */
internal class LanTransportLoopbackE2eTest {

    @Test
    fun `two devices exchange encrypted clipboard over TCP`() = runBlocking {
        val key = secureRandomBytes(32)
        val cipher = AesGcmPayloadCipher()
        val userId = "user-loopback"
        val nodeA = Node(deviceId = "device-a", userId = userId, key = key, cipher = cipher)
        val nodeB = Node(deviceId = "device-b", userId = userId, key = key, cipher = cipher)

        val jobA = launch(Dispatchers.Default) { nodeA.transport.run() }
        val jobB = launch(Dispatchers.Default) { nodeB.transport.run() }
        val fallbackA = launch(Dispatchers.Default) {
            nodeA.transport.serverPackets().collect { nodeA.fallback += it }
        }
        val fallbackB = launch(Dispatchers.Default) {
            nodeB.transport.serverPackets().collect { nodeB.fallback += it }
        }

        try {
            waitUntil("оба LAN-сервера поднялись") {
                nodeA.discovery.advertisement != null && nodeB.discovery.advertisement != null
            }
            nodeA.discovery.publish(
                listOf(
                    LanPeer(
                        deviceId = nodeB.deviceId,
                        userId = userId,
                        host = "127.0.0.1",
                        port = nodeB.discovery.advertisement!!.port,
                    ),
                ),
            )
            nodeB.discovery.publish(
                listOf(
                    LanPeer(
                        deviceId = nodeA.deviceId,
                        userId = userId,
                        host = "127.0.0.1",
                        port = nodeA.discovery.advertisement!!.port,
                    ),
                ),
            )

            val text = "LAN loopback ${secureRandomBytes(4).toHex()}"
            nodeA.outgoing.publish(
                SyncPacket(
                    eventId = "event-lan-1",
                    text = text,
                    copiedAtMillis = System.currentTimeMillis(),
                ),
            )

            waitUntil("B получил пакет по LAN") { nodeB.sink.received.isNotEmpty() }
            assertEquals(text, nodeB.sink.received.single().text)
            assertEquals(nodeA.deviceId, nodeB.sink.received.single().originDeviceId)
            assertTrue(
                nodeA.fallback.isEmpty(),
                "ACK по LAN должен снять необходимость отправлять пакет на сервер",
            )
        } finally {
            jobA.cancelAndJoin()
            jobB.cancelAndJoin()
            fallbackA.cancelAndJoin()
            fallbackB.cancelAndJoin()
        }
    }

    private suspend fun waitUntil(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            delay(POLL_MILLIS)
        }
        fail("$message: не дождались за $TIMEOUT_MILLIS мс")
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    }

    private inner class Node(
        val deviceId: String,
        userId: String,
        key: ByteArray,
        cipher: AesGcmPayloadCipher,
    ) {
        val outgoing = FakePacketSource()
        val sink = FakeIncomingSink()
        val fallback = mutableListOf<SyncPacket>()
        val discovery = CapturingDiscovery()

        val transport = LanSyncTransport(
            server = createLanServer(),
            discoveryFactory = LanDiscoveryFactory { advertisement ->
                discovery.advertisement = advertisement
                discovery
            },
            dialer = createLanDialer(),
            keyProvider = FakeKeyProvider(key),
            identity = FakeDeviceIdentityStore(deviceId),
            session = FakeSignedInSession(userId),
            outgoing = outgoing,
            processor = IncomingPacketProcessor(cipher, sink),
            settings = FakeClipboardSettingsStore(),
            cipher = cipher,
            clock = EpochClock.System,
        )
    }

    /** Дискавери, который запоминает анонс и отдаёт пиров от теста. */
    private class CapturingDiscovery : LanDiscovery {

        private val state = MutableStateFlow<List<LanPeer>>(emptyList())
        var advertisement: LanAdvertisement? = null
        override val peers: StateFlow<List<LanPeer>> = state

        override suspend fun start() = Unit

        override suspend fun stop() = Unit

        fun publish(peers: List<LanPeer>) {
            state.value = peers
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
    }
}
