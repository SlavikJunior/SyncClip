package io.github.slavikjunior.syncclip.feature.sync.impl.data.transport

import io.github.slavikjunior.syncclip.core.lan.LanPeer
import io.github.slavikjunior.syncclip.feature.sync.impl.BufferLanStream
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeClipboardSettingsStore
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeDeviceIdentityStore
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeIncomingSink
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeKeyProvider
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeLanDialer
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeLanDiscovery
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeLanServer
import io.github.slavikjunior.syncclip.feature.sync.impl.FakePacketSource
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeSignedInSession
import io.github.slavikjunior.syncclip.feature.sync.impl.TestClock
import io.github.slavikjunior.syncclip.feature.sync.impl.data.remote.ClientMessage
import io.github.slavikjunior.syncclip.feature.sync.impl.data.remote.ServerMessage
import io.github.slavikjunior.syncclip.feature.sync.impl.data.remote.WsCodec
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncPacket
import io.github.slavikjunior.syncclip.feature.sync.impl.lanFrame
import io.github.slavikjunior.syncclip.shared.crypto.AesGcmPayloadCipher
import io.github.slavikjunior.syncclip.shared.crypto.PayloadHash
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * LAN-транспорт без сети: дискавери и сокеты фейковые, шифрование настоящее.
 *
 * Проверяется поведение из FR-2.5/2.6: параллельная отправка всем пирам, ACK
 * снимает fallback, отсутствие ACK отправляет пакет на сервер, входящий пакет
 * применяется через общий `ClipboardIncomingSink`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class LanSyncTransportTest {

    private val key = ByteArray(32) { (it + 1).toByte() }
    private val cipher = AesGcmPayloadCipher { size -> ByteArray(size) { 7 } }
    private val clock = TestClock()
    private val settings = FakeClipboardSettingsStore()
    private val outgoing = FakePacketSource()
    private val sink = FakeIncomingSink()
    private val server = FakeLanServer()
    private val discovery = FakeLanDiscovery()
    private val identity = FakeDeviceIdentityStore(DEVICE_ID)
    private val session = FakeSignedInSession(USER_ID)
    private val processor = IncomingPacketProcessor(cipher, sink)

    private val peerB = LanPeer(deviceId = "device-b", userId = USER_ID, host = "127.0.0.1", port = 41_001)
    private val peerC = LanPeer(deviceId = "device-c", userId = USER_ID, host = "127.0.0.1", port = 41_002)

    @Test
    fun `ack from a peer suppresses server fallback`() = runTest {
        discovery.publish(listOf(peerB))
        val dialer = FakeLanDialer(mapOf(peerB.port to BufferLanStream(ackFrame("event-1"))))
        val fallback = mutableListOf<SyncPacket>()
        startTransport(dialer, fallback)

        outgoing.publish(packet("event-1", "hello"))
        runCurrent()

        assertTrue(dialer.dialedPorts.contains(peerB.port))
        assertTrue(fallback.isEmpty(), "пакет доставлен по LAN — сервер не нужен")
    }

    @Test
    fun `no peers means server fallback`() = runTest {
        val dialer = FakeLanDialer(emptyMap())
        val fallback = mutableListOf<SyncPacket>()
        startTransport(dialer, fallback)

        outgoing.publish(packet("event-2", "hello"))
        runCurrent()

        assertEquals(listOf("event-2"), fallback.map { it.eventId })
        assertTrue(dialer.dialedPorts.isEmpty())
    }

    @Test
    fun `peer without ack falls back to server`() = runTest {
        discovery.publish(listOf(peerB))
        val dialer = FakeLanDialer(mapOf(peerB.port to BufferLanStream()))
        val fallback = mutableListOf<SyncPacket>()
        startTransport(dialer, fallback)

        outgoing.publish(packet("event-3", "hello"))
        runCurrent()

        assertEquals(listOf("event-3"), fallback.map { it.eventId })
    }

    @Test
    fun `packet is sent to every peer in parallel`() = runTest {
        discovery.publish(listOf(peerB, peerC))
        val dialer = FakeLanDialer(
            mapOf(
                peerB.port to BufferLanStream(ackFrame("event-4")),
                peerC.port to BufferLanStream(ackFrame("event-4")),
            ),
        )
        val fallback = mutableListOf<SyncPacket>()
        startTransport(dialer, fallback)

        outgoing.publish(packet("event-4", "hello"))
        runCurrent()

        assertEquals(setOf(peerB.port, peerC.port), dialer.dialedPorts.toSet())
        assertTrue(fallback.isEmpty())
    }

    @Test
    fun `incoming LAN packet is applied and acknowledged`() = runTest {
        startTransport(FakeLanDialer(emptyMap()), mutableListOf())
        val handler = assertNotNull(server.handler)

        val encrypted = cipher.encrypt("from peer", key)
        val frame = WsCodec.encode(
            ClientMessage.SyncEvent(
                eventId = "event-in",
                originDeviceId = "device-b",
                encryptedPayload = encrypted,
                payloadHash = PayloadHash.of("from peer"),
                timestamp = clock.nowMillis(),
            ),
        )
        val stream = BufferLanStream(lanFrame(frame))

        handler(stream)

        assertEquals("from peer", sink.received.single().text)
        val ack = assertIs<ServerMessage.Ack>(WsCodec.decode(assertNotNull(stream.nextWrittenFrame())))
        assertEquals("event-in", ack.eventId)
        assertEquals(DEVICE_ID, ack.deviceId)
    }

    @Test
    fun `own event is ignored without ack`() = runTest {
        startTransport(FakeLanDialer(emptyMap()), mutableListOf())
        val handler = assertNotNull(server.handler)

        val frame = WsCodec.encode(
            ClientMessage.SyncEvent(
                eventId = "event-own",
                originDeviceId = DEVICE_ID,
                encryptedPayload = cipher.encrypt("mine", key),
                payloadHash = PayloadHash.of("mine"),
                timestamp = clock.nowMillis(),
            ),
        )
        val stream = BufferLanStream(lanFrame(frame))

        handler(stream)

        assertTrue(sink.received.isEmpty())
        assertNull(stream.nextWrittenFrame())
    }

    @Test
    fun `offline transport does not send or fall back`() = runTest {
        discovery.publish(listOf(peerB))
        settings.setOnline(false)
        val dialer = FakeLanDialer(mapOf(peerB.port to BufferLanStream(ackFrame("event-5"))))
        val fallback = mutableListOf<SyncPacket>()
        startTransport(dialer, fallback)

        outgoing.publish(packet("event-5", "hello"))
        runCurrent()

        assertTrue(dialer.dialedPorts.isEmpty())
        assertTrue(fallback.isEmpty())
        assertTrue(!server.started)
    }

    private fun packet(eventId: String, text: String) = SyncPacket(
        eventId = eventId,
        text = text,
        copiedAtMillis = clock.nowMillis(),
    )

    private fun ackFrame(eventId: String): ByteArray =
        lanFrame(WsCodec.encode(ClientMessage.Ack(eventId = eventId, deviceId = "device-b")))

    private fun TestScope.startTransport(
        dialer: FakeLanDialer,
        fallback: MutableList<SyncPacket>,
    ) {
        val transport = LanSyncTransport(
            server = server,
            discoveryFactory = { discovery },
            dialer = dialer,
            keyProvider = FakeKeyProvider(key),
            identity = identity,
            session = session,
            outgoing = outgoing,
            processor = processor,
            settings = settings,
            cipher = cipher,
            clock = clock,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { transport.run() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            transport.serverPackets().collect { fallback += it }
        }
        runCurrent()
    }

    private companion object {
        const val DEVICE_ID = "device-a"
        const val USER_ID = "user-1"
    }
}
