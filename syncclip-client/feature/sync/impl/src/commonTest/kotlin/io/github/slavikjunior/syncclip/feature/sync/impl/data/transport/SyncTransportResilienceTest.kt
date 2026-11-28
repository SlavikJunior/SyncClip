package io.github.slavikjunior.syncclip.feature.sync.impl.data.transport

import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncConnectionStatus
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeClipboardSettingsStore
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeDeviceIdentityStore
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeIncomingSink
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeKeyProvider
import io.github.slavikjunior.syncclip.feature.sync.impl.FakePacketSource
import io.github.slavikjunior.syncclip.feature.sync.impl.TestClock
import io.github.slavikjunior.syncclip.feature.sync.impl.data.remote.SyncSocket
import io.github.slavikjunior.syncclip.feature.sync.impl.data.remote.SyncSocketFactory
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncPacket
import io.github.slavikjunior.syncclip.shared.crypto.AesGcmPayloadCipher
import io.github.slavikjunior.syncclip.shared.crypto.PayloadHash
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Обрывы и неожиданные кадры в WAN-транспорте (FR-2.3).
 *
 * Проверяется то, чего не видно в e2e: клиент не должен ни падать, ни молотить
 * переподключения без backoff, ни терять уже открытое соединение из-за одного
 * мусорного кадра.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SyncTransportResilienceTest {

    private val key = ByteArray(32) { (it + 1).toByte() }
    private val cipher = AesGcmPayloadCipher { size -> ByteArray(size) { 7 } }
    private val keyProvider = FakeKeyProvider(key)
    private val identity = FakeDeviceIdentityStore(DEVICE_ID)
    private val outgoing = FakePacketSource()
    private val sink = FakeIncomingSink()
    private val settings = FakeClipboardSettingsStore()
    private val clock = TestClock()

    private lateinit var transport: SyncTransport

    @Test
    fun `connection dropped mid-stream reconnects and keeps applying packets`() = runTest {
        val first = ScriptedSyncSocket(incomingFactory = { flow { throw TransportFailure("peer reset") } })
        val second = ScriptedSyncSocket().apply { deliver(eventJson("event-2", "after reconnect")) }
        val factory = QueueSocketFactory(first, second)
        startTransport(factory)

        // Первая связь рвётся сразу после рукопожатия.
        assertEquals(1, factory.attempts)
        assertEquals(SyncConnectionStatus.OFFLINE, transport.connectionStatus.value)

        advanceTimeBy(1_001)
        runCurrent()

        assertEquals(2, factory.attempts)
        assertEquals(SyncConnectionStatus.ONLINE, transport.connectionStatus.value)
        assertEquals(listOf("after reconnect"), sink.received.map { it.text })
    }

    @Test
    fun `silent connection is dropped after three ping intervals`() = runTest {
        // Сервер принимает соединение и замолкает: так выглядит полуоткрытый
        // TCP после смены сети — обрыва нет, но кадров тоже нет.
        val factory = QueueSocketFactory(ScriptedSyncSocket(), ScriptedSyncSocket())
        startTransport(factory)
        assertEquals(1, factory.attempts)
        assertEquals(SyncConnectionStatus.ONLINE, transport.connectionStatus.value)

        // Часы ушли вперёд, а входящих кадров не было — метка живости старая.
        clock.advance(121_000)
        advanceTimeBy(30_001)
        runCurrent()

        // Мёртвое соединение закрыто и заменено новым.
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(2, factory.attempts)
        assertEquals(SyncConnectionStatus.ONLINE, transport.connectionStatus.value)
    }

    @Test
    fun `incoming pong keeps an idle connection alive`() = runTest {
        val socket = ScriptedSyncSocket()
        val factory = QueueSocketFactory(socket)
        startTransport(factory)

        // Имитируем ответы сервера на heartbeat: соединение не должно рваться.
        repeat(5) {
            clock.advance(30_000)
            socket.deliver("""{"type":"PONG","timestamp":1}""")
            advanceTimeBy(30_001)
            runCurrent()
        }

        assertEquals(1, factory.attempts)
        assertEquals(SyncConnectionStatus.ONLINE, transport.connectionStatus.value)
    }

    @Test
    fun `handshake closed immediately uses backoff and suspends after five attempts`() = runTest {
        val factory = EndlessClosingSocketFactory(connectionMillis = 0)
        startTransport(factory)

        // Попытка 1 → пауза 1с, 2 → 2с, 3 → 4с, 4 → 8с.
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(2, factory.attempts)

        advanceTimeBy(2_001)
        runCurrent()
        assertEquals(3, factory.attempts)

        advanceTimeBy(4_001)
        runCurrent()
        assertEquals(4, factory.attempts)

        advanceTimeBy(8_001)
        runCurrent()
        assertEquals(5, factory.attempts)
        assertEquals(SyncConnectionStatus.SUSPENDED, transport.connectionStatus.value)

        // Попытки не прекращаются: сеть может вернуться сама, но пауза растёт.
        advanceTimeBy(16_001)
        runCurrent()
        assertEquals(6, factory.attempts)
    }

    @Test
    fun `pausing sync is not counted as a connection failure`() = runTest {
        val factory = QueueSocketFactory(*Array(32) { ScriptedSyncSocket() })
        startTransport(factory)

        // Пользователь шесть раз ставит паузу и возвращается в Online:
        // каждый раз сокет закрывается намеренно.
        repeat(6) {
            settings.setOnline(false)
            runCurrent()
            advanceTimeBy(1_001)
            settings.setOnline(true)
            runCurrent()
            advanceTimeBy(1_001)
        }
        assertEquals(SyncConnectionStatus.ONLINE, transport.connectionStatus.value)

        // Теперь сервер действительно недоступен: одна неудача не должна
        // сразу давать SUSPENDED из-за накопленных «пауз».
        factory.failOpens = true
        settings.setOnline(false)
        runCurrent()
        advanceTimeBy(1_001)
        settings.setOnline(true)
        runCurrent()
        advanceTimeBy(1_001)
        runCurrent()

        assertEquals(SyncConnectionStatus.OFFLINE, transport.connectionStatus.value)
    }

    @Test
    fun `long lived connection resets the failure counter`() = runTest {
        val factory = EndlessClosingSocketFactory(connectionMillis = 15_000)
        startTransport(factory)

        repeat(6) {
            advanceTimeBy(15_001)
            runCurrent()
            advanceTimeBy(1_001)
            runCurrent()
            assertTrue(
                transport.connectionStatus.value != SyncConnectionStatus.SUSPENDED,
                "здоровое соединение не должно приближать SUSPENDED",
            )
        }

        assertEquals(7, factory.attempts)
    }

    @Test
    fun `garbage and unknown frames do not close the connection`() = runTest {
        val socket = ScriptedSyncSocket().apply {
            deliver("not a json")
            deliver("")
            deliver("""{"type":"UNKNOWN_FROM_FUTURE_SERVER","payload":{}}""")
            deliver("""{"type":"SYNC_EVENT"}""")
            deliver("""{"type":42}""")
            deliver("""{"type":"ERROR","code":"bad_request","message":"nope"}""")
        }
        val factory = QueueSocketFactory(socket)
        startTransport(factory)

        // Ни одно из сообщений не подтверждается и не применяется.
        assertTrue(sink.received.isEmpty())
        assertTrue(socket.sent.isEmpty())
        assertFalse(socket.closed, "мусорный кадр не должен закрывать соединение")
        assertEquals(SyncConnectionStatus.ONLINE, transport.connectionStatus.value)

        // Соединение по-прежнему рабочее.
        socket.deliver(eventJson("event-1", "valid"))
        runCurrent()

        assertEquals(listOf("valid"), sink.received.map { it.text })
        assertTrue(socket.sent.single().contains("\"ACK\""))
    }

    @Test
    fun `oversized frame is ignored without crashing`() = runTest {
        val socket = ScriptedSyncSocket().apply {
            deliver("{\"type\":\"SYNC_EVENT\",\"eventId\":\"" + "a".repeat(2 * 1024 * 1024) + "\"}")
        }
        startTransport(QueueSocketFactory(socket))

        assertTrue(sink.received.isEmpty())
        assertEquals(SyncConnectionStatus.ONLINE, transport.connectionStatus.value)
    }

    @Test
    fun `send failure reconnects instead of crashing the transport`() = runTest {
        val broken = ScriptedSyncSocket(sendFailure = TransportFailure("broken pipe"))
        val healthy = ScriptedSyncSocket()
        val factory = QueueSocketFactory(broken, healthy)
        startTransport(factory)

        outgoing.publish(SyncPacket(eventId = "event-1", text = "hello", copiedAtMillis = clock.nowMillis()))
        runCurrent()

        assertTrue(broken.closed)
        advanceTimeBy(1_001)
        runCurrent()

        assertEquals(2, factory.attempts)
        assertEquals(SyncConnectionStatus.ONLINE, transport.connectionStatus.value)
    }

    private fun TestScope.startTransport(factory: SyncSocketFactory): SyncTransport {
        transport = SyncTransport(
            socketFactory = factory,
            keyProvider = keyProvider,
            identity = identity,
            serverPackets = outgoing,
            incomingSink = sink,
            settings = settings,
            cipher = cipher,
            clock = clock,
            timeSource = SchedulerTimeSource(testScheduler),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { transport.run() }
        runCurrent()
        return transport
    }

    private fun eventJson(eventId: String, text: String): String {
        val encrypted = cipher.encrypt(text, key)
        return """
            {"type":"SYNC_EVENT","eventId":"$eventId","originDeviceId":"device-b",
             "encryptedPayload":"$encrypted","payloadHash":"${PayloadHash.of(text)}",
             "timestamp":${clock.nowMillis()}}
        """.trimIndent()
    }

    private companion object {
        const val DEVICE_ID = "device-a"
    }
}

/** Сбой транспорта в терминах теста (общий код, без java.io). */
internal class TransportFailure(message: String) : Exception(message)

/** Сокет, поведение которого задаёт тест. */
internal class ScriptedSyncSocket(
    private val incomingFactory: (() -> Flow<String>)? = null,
    private val sendFailure: Throwable? = null,
) : SyncSocket {

    /** `Channel` сохраняет кадры, доставленные до подписки транспорта. */
    private val frames = Channel<String>(Channel.UNLIMITED)

    val sent: MutableList<String> = mutableListOf()
    var closed: Boolean = false

    override suspend fun send(text: String) {
        sendFailure?.let { throw it }
        sent += text
    }

    override fun incoming(): Flow<String> = incomingFactory?.invoke() ?: frames.receiveAsFlow()

    override suspend fun close() {
        closed = true
        // Закрытие канала завершает `incoming()` так же, как закрытие сокета.
        frames.close()
    }

    suspend fun deliver(text: String) {
        frames.send(text)
    }
}

/** Отдаёт заранее подготовленные сокеты по очереди. */
internal class QueueSocketFactory(vararg sockets: SyncSocket) : SyncSocketFactory {

    private val queue = ArrayDeque(sockets.toList())
    var attempts: Int = 0
        private set

    /** Когда задано — `open` падает (сервер недоступен). */
    var failOpens: Boolean = false

    override suspend fun open(): SyncSocket {
        attempts++
        if (failOpens) throw TransportFailure("connection refused")
        return queue.removeFirstOrNull() ?: ScriptedSyncSocket()
    }
}

/**
 * Сервер, который принимает рукопожатие и закрывает связь через [connectionMillis].
 *
 * `connectionMillis = 0` воспроизводит сервер, который отвергает уже открытый
 * WebSocket (несовпадение `deviceId`, отзыв сессии на уровне прокси).
 */
internal class EndlessClosingSocketFactory(
    private val connectionMillis: Long = 0,
) : SyncSocketFactory {

    var attempts: Int = 0
        private set

    override suspend fun open(): SyncSocket {
        attempts++
        return ScriptedSyncSocket(
            incomingFactory = {
                flow {
                    delay(connectionMillis)
                    throw TransportFailure("closed by server")
                }
            },
        )
    }
}

/** Часы теста: `markNow()` берёт виртуальное время шедулера. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SchedulerTimeSource(
    private val scheduler: TestCoroutineScheduler,
) : TimeSource {

    override fun markNow(): TimeMark = Mark(scheduler, scheduler.currentTime)

    private class Mark(
        private val scheduler: TestCoroutineScheduler,
        private val startedAt: Long,
    ) : TimeMark {
        override fun elapsedNow(): Duration =
            (scheduler.currentTime - startedAt).milliseconds
    }
}
