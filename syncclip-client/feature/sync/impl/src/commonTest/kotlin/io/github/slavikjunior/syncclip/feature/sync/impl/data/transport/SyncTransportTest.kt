package io.github.slavikjunior.syncclip.feature.sync.impl.data.transport

import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncConnectionStatus
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeClipboardSettingsStore
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeDeviceIdentityStore
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeIncomingSink
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeKeyProvider
import io.github.slavikjunior.syncclip.feature.sync.impl.FakePacketSource
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeSessionRefresher
import io.github.slavikjunior.syncclip.feature.sync.impl.TestClock
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncPacket
import io.github.slavikjunior.syncclip.shared.crypto.AesGcmPayloadCipher
import io.github.slavikjunior.syncclip.shared.crypto.PayloadHash
import io.github.slavikjunior.syncclip.feature.sync.impl.data.remote.SyncSocket
import io.github.slavikjunior.syncclip.feature.sync.impl.data.remote.SyncSocketFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Поведение транспорта без сети: фейковый сокет, настоящие шифрование и хеши.
 *
 * Проверяется ровно то, что нельзя увидеть в e2e: формат кадра, фильтрация
 * чужих/битых пакетов, ACK, backoff и остановка по `SESSION_REVOKED`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SyncTransportTest {

    private val key = ByteArray(32) { (it + 1).toByte() }
    private val cipher = AesGcmPayloadCipher { size -> ByteArray(size) { 7 } }
    private val keyProvider = FakeKeyProvider(key)
    private val identity = FakeDeviceIdentityStore(DEVICE_ID)
    private val outgoing = FakePacketSource()
    private val sink = FakeIncomingSink()
    private val settings = FakeClipboardSettingsStore()
    private val clock = TestClock()
    private val factory = FakeSocketFactory()
    private val transport = SyncTransport(
        socketFactory = factory,
        keyProvider = keyProvider,
        identity = identity,
        serverPackets = outgoing,
        incomingSink = sink,
        settings = settings,
        cipher = cipher,
        clock = clock,
    )

    @Test
    fun `outgoing packet is encrypted and framed`() = runTest {
        startTransport()
        val socket = factory.created.single()

        outgoing.publish(SyncPacket(eventId = "event-1", text = "secret text", copiedAtMillis = clock.nowMillis()))
        runCurrent()

        val frame = socket.sent.single()
        val body = Json.parseToJsonElement(frame).jsonObject
        assertEquals("SYNC_EVENT", body["type"]?.jsonPrimitive?.content)
        assertEquals(DEVICE_ID, body["originDeviceId"]?.jsonPrimitive?.content)
        assertEquals(
            PayloadHash.of("secret text"),
            body["payloadHash"]?.jsonPrimitive?.content,
        )
        assertEquals(
            "secret text",
            cipher.decrypt(body["encryptedPayload"]!!.jsonPrimitive.content, key),
        )
    }

    @Test
    fun `incoming packet is applied and acknowledged`() = runTest {
        startTransport()
        val socket = factory.created.single()

        socket.deliver(serverEvent(eventId = "event-1", text = "from peer"))
        runCurrent()

        val received = sink.received.single()
        assertEquals("from peer", received.text)
        assertEquals("device-b", received.originDeviceId)

        val ack = Json.parseToJsonElement(socket.sent.last()).jsonObject
        assertEquals("ACK", ack["type"]?.jsonPrimitive?.content)
        assertEquals("event-1", ack["eventId"]?.jsonPrimitive?.content)
        assertEquals(DEVICE_ID, ack["deviceId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `own event is ignored`() = runTest {
        startTransport()
        val socket = factory.created.single()

        socket.deliver(serverEvent(eventId = "event-1", text = "mine", origin = DEVICE_ID))
        runCurrent()

        assertTrue(sink.received.isEmpty())
        assertTrue(socket.sent.isEmpty())
    }

    @Test
    fun `payload hash mismatch is ignored`() = runTest {
        startTransport()
        val socket = factory.created.single()

        socket.deliver(
            serverEvent(eventId = "event-1", text = "tampered", hash = "0".repeat(64)),
        )
        runCurrent()

        assertTrue(sink.received.isEmpty())
        assertTrue(socket.sent.isEmpty())
    }

    @Test
    fun `packet encrypted with another key is ignored`() = runTest {
        startTransport()
        val socket = factory.created.single()
        val foreignCipher = AesGcmPayloadCipher { size -> ByteArray(size) { 9 } }
        val encrypted = foreignCipher.encrypt("foreign", ByteArray(32) { 42 })

        socket.deliver(
            eventJson(
                eventId = "event-1",
                origin = "device-b",
                encryptedPayload = encrypted,
                hash = PayloadHash.of("foreign"),
            ),
        )
        runCurrent()

        assertTrue(sink.received.isEmpty())
    }

    @Test
    fun `offline mode does not send packets`() = runTest {
        startTransport()
        val socket = factory.created.single()

        settings.setOnline(false)
        runCurrent()
        val sentBefore = socket.sent.size

        outgoing.publish(SyncPacket(eventId = "event-2", text = "offline text", copiedAtMillis = clock.nowMillis()))
        runCurrent()

        assertEquals(sentBefore, socket.sent.size)
    }

    @Test
    fun `failed connection is retried with backoff`() = runTest {
        factory.failuresBeforeSuccess = 1
        startTransport()

        assertEquals(SyncConnectionStatus.OFFLINE, transport.connectionStatus.value)
        assertEquals(1, factory.attempts)
        assertTrue(factory.created.isEmpty())

        advanceTimeBy(1_001)
        runCurrent()

        assertEquals(SyncConnectionStatus.ONLINE, transport.connectionStatus.value)
        assertEquals(2, factory.attempts)
    }

    @Test
    fun `five failures suspend the transport`() = runTest {
        factory.failuresBeforeSuccess = 5
        startTransport()

        // Попытки 2..5: задержки 1s, 2s, 4s, 8s. Дальше сервер «оживает»,
        // поэтому ждём ровно до пятой неудачи.
        advanceTimeBy(1_001)
        runCurrent()
        advanceTimeBy(2_001)
        runCurrent()
        advanceTimeBy(4_001)
        runCurrent()
        advanceTimeBy(8_001)
        runCurrent()

        assertEquals(5, factory.attempts)
        assertEquals(SyncConnectionStatus.SUSPENDED, transport.connectionStatus.value)
    }

    @Test
    fun `token is refreshed before every connection attempt`() = runTest {
        val refresher = FakeSessionRefresher()
        val local = SyncTransport(
            socketFactory = factory,
            keyProvider = keyProvider,
            identity = identity,
            serverPackets = outgoing,
            incomingSink = sink,
            settings = settings,
            cipher = cipher,
            clock = clock,
            sessionRefresher = refresher,
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { local.run() }
        runCurrent()

        assertEquals(1, refresher.calls)
        assertEquals(1, factory.created.size)
    }

    @Test
    fun `offline at start postpones the connection`() = runTest {
        settings.setOnline(false)
        startTransport()

        assertEquals(SyncConnectionStatus.OFFLINE, transport.connectionStatus.value)
        assertTrue(factory.created.isEmpty())

        settings.setOnline(true)
        runCurrent()

        assertEquals(SyncConnectionStatus.ONLINE, transport.connectionStatus.value)
        assertEquals(1, factory.created.size)
    }

    @Test
    fun `switching to offline closes the connection`() = runTest {
        startTransport()
        val socket = factory.created.single()

        settings.setOnline(false)
        runCurrent()

        assertTrue(socket.closed, "в Offline WebSocket должен закрываться (FR-2.9)")
    }

    @Test
    fun `session revoked stops the transport`() = runTest {
        val job = startTransport()
        val socket = factory.created.single()

        socket.deliver("""{"type":"SESSION_REVOKED","reason":"device_removed","message":"removed"}""")
        runCurrent()

        assertEquals(SyncConnectionStatus.SUSPENDED, transport.connectionStatus.value)
        assertTrue(job.isCompleted, "после отзыва сессии транспорт не переподключается")
        assertTrue(socket.closed)
    }

    @Test
    fun `transport waits for the master password`() = runTest {
        keyProvider.value = null

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { transport.run() }
        runCurrent()

        assertEquals(SyncConnectionStatus.LOCKED, transport.connectionStatus.value)
        assertTrue(factory.created.isEmpty())
        job.cancel()
    }

    private fun TestScope.startTransport(): Job {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            transport.run()
        }
        runCurrent()
        return job
    }

    private fun serverEvent(
        eventId: String,
        text: String,
        origin: String = "device-b",
        hash: String = PayloadHash.of(text),
    ): String = eventJson(
        eventId = eventId,
        origin = origin,
        encryptedPayload = cipher.encrypt(text, key),
        hash = hash,
    )

    private fun eventJson(
        eventId: String,
        origin: String,
        encryptedPayload: String,
        hash: String,
    ): String = """
        {"type":"SYNC_EVENT","eventId":"$eventId","originDeviceId":"$origin",
         "encryptedPayload":"$encryptedPayload","payloadHash":"$hash","timestamp":${clock.nowMillis()}}
    """.trimIndent()

    private companion object {
        const val DEVICE_ID = "device-a"
    }
}

/** Сокет, которым управляет тест. */
internal class FakeSyncSocket : SyncSocket {

    private val frames = MutableSharedFlow<String>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    val sent: MutableList<String> = mutableListOf()
    var closed: Boolean = false

    override suspend fun send(text: String) {
        sent += text
    }

    override fun incoming(): Flow<String> = frames

    override suspend fun close() {
        closed = true
    }

    suspend fun deliver(text: String) {
        frames.emit(text)
    }
}

/** Фабрика, которая отдаёт фейковые сокеты и умеет падать первые N раз. */
internal class FakeSocketFactory : SyncSocketFactory {

    val created: MutableList<FakeSyncSocket> = mutableListOf()
    var attempts: Int = 0
    var failuresBeforeSuccess: Int = 0

    override suspend fun open(): SyncSocket {
        attempts++
        if (failuresBeforeSuccess > 0) {
            failuresBeforeSuccess--
            throw IllegalStateException("network is down")
        }
        return FakeSyncSocket().also { created += it }
    }
}
