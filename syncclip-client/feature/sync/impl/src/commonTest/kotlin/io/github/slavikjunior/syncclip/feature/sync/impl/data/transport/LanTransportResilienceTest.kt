package io.github.slavikjunior.syncclip.feature.sync.impl.data.transport

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
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncPacket
import io.github.slavikjunior.syncclip.shared.crypto.AesGcmPayloadCipher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Отказы LAN-канала: очередь fallback не растёт без предела, а молчащий пир
 * не держит соединение вечно.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class LanTransportResilienceTest {

    private val key = ByteArray(32) { (it + 1).toByte() }
    private val cipher = AesGcmPayloadCipher { size -> ByteArray(size) { 7 } }
    private val clock = TestClock()
    private val settings = FakeClipboardSettingsStore()
    private val outgoing = FakePacketSource()
    private val sink = FakeIncomingSink()
    private val server = FakeLanServer()
    private val discovery = FakeLanDiscovery()
    private val identity = FakeDeviceIdentityStore("device-a")
    private val session = FakeSignedInSession("user-1")

    private val transport = LanSyncTransport(
        server = server,
        discoveryFactory = { discovery },
        dialer = FakeLanDialer(emptyMap()),
        keyProvider = FakeKeyProvider(key),
        identity = identity,
        session = session,
        outgoing = outgoing,
        processor = IncomingPacketProcessor(cipher, sink),
        settings = settings,
        cipher = cipher,
        clock = clock,
    )

    @Test
    fun `fallback queue is bounded when nobody drains it`() = runTest {
        start()

        // Пиров нет, WAN-транспорт не подключён: каждый пакет уходит в fallback.
        repeat(300) { index ->
            outgoing.publish(packet("event-$index"))
        }
        runCurrent()

        val received = mutableListOf<SyncPacket>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            transport.serverPackets().take(64).toList(received)
        }
        runCurrent()

        // UNLIMITED-очередь вернула бы все 300 пакетов и росла бы бесконечно.
        assertTrue(
            received.size <= 32,
            "очередь fallback должна быть ограничена, получено ${received.size}",
        )
        assertTrue(received.isNotEmpty(), "последние пакеты должны остаться")
        assertEquals("event-299", received.last().eventId)
    }

    @Test
    fun `failed LAN startup is retried after the cooldown`() = runTest {
        server.failStarts = 1
        start()

        assertTrue(!server.started, "первая попытка поднять LAN должна упасть")

        // Пока LAN недоступен, пакеты всё равно уходят на сервер.
        outgoing.publish(packet("event-1"))
        runCurrent()

        advanceTimeBy(30_001)
        runCurrent()

        assertTrue(server.started, "через cooldown LAN должен подняться снова")
    }

    @Test
    fun `silent peer connection gets a read timeout`() = runTest {
        start()

        // Пир подключился и молчит: обработчик обязан выставить таймаут чтения,
        // иначе сокет и корутина висят до перезапуска приложения.
        val silent = BufferLanStream()
        server.handler?.invoke(silent)

        assertEquals(5_000L, silent.lastReadTimeoutMillis)
    }

    private fun TestScope.start() {
        discovery.publish(emptyList())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { transport.run() }
        runCurrent()
    }

    private fun packet(eventId: String) = SyncPacket(
        eventId = eventId,
        text = "text-$eventId",
        copiedAtMillis = clock.nowMillis(),
    )
}
