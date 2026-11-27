package io.github.slavikjunior.syncclip.feature.sync.impl

import io.github.slavikjunior.syncclip.core.clipboard.ClipboardCapture
import io.github.slavikjunior.syncclip.feature.sync.impl.data.repository.ClipboardCaptureRepository
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncPacket
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.AcceptLocalCaptureUseCase
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.DebounceClipboardUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class ClipboardCaptureRepositoryTest {

    private val gateway = FakeClipboardGateway()
    private val writer = FakeHistoryWriter()
    private val settings = FakeClipboardSettingsStore()
    private val clock = TestClock()

    private val repository = ClipboardCaptureRepository(
        gateway = gateway,
        pendingShares = FakePendingShareSource(),
        historyWriter = writer,
        settings = settings,
        accept = AcceptLocalCaptureUseCase(),
        debounce = DebounceClipboardUseCase(),
        clock = clock,
    )

    @Test
    fun `local copy goes to history and to outgoing stream`() = runTest {
        val outgoing = collectPackets()

        gateway.publish(ClipboardCapture("hello", clock.nowMillis()))
        advanceTimeBy(150)
        runCurrent()

        assertEquals(listOf("hello"), writer.saved.map { it.text })
        assertEquals(null, writer.saved.single().sourceDeviceId)
        assertEquals(listOf("hello"), outgoing.map { it.text })
    }

    @Test
    fun `offline copy is saved but not sent`() = runTest {
        settings.setOnline(false)
        val outgoing = collectPackets()

        gateway.publish(ClipboardCapture("offline text", clock.nowMillis()))
        advanceTimeBy(150)
        runCurrent()

        assertEquals(listOf("offline text"), writer.saved.map { it.text })
        assertTrue(outgoing.isEmpty(), "в Offline пакеты не уходят в сеть")
    }

    @Test
    fun `oversized copy is dropped entirely`() = runTest {
        val outgoing = collectPackets()
        val oversized = "a".repeat(512 * 1024 + 1)

        gateway.publish(ClipboardCapture(oversized, clock.nowMillis()))
        advanceTimeBy(150)
        runCurrent()

        assertTrue(writer.saved.isEmpty())
        assertTrue(outgoing.isEmpty())
    }

    @Test
    fun `text already sent by the share extension goes to history only`() = runTest {
        val outgoing = collectPackets()
        val shares = FakePendingShareSource()
        val local = ClipboardCaptureRepository(
            gateway = gateway,
            pendingShares = shares,
            historyWriter = writer,
            settings = settings,
            accept = AcceptLocalCaptureUseCase(),
            debounce = DebounceClipboardUseCase(),
            clock = clock,
        )
        shares.publishAlreadySent(listOf("shared from iOS"))

        val job = launch(UnconfinedTestDispatcher(testScheduler)) { local.run() }
        runCurrent()

        assertEquals(listOf("shared from iOS"), writer.saved.map { it.text })
        assertTrue(outgoing.isEmpty(), "расширение уже отправило пакет")
        job.cancel()
    }

    @Test
    fun `capture current takes text from clipboard`() = runTest {
        gateway.current = "from share sheet"

        val accepted = repository.captureCurrent()

        assertTrue(accepted)
        assertEquals(listOf("from share sheet"), writer.saved.map { it.text })
    }

    @Test
    fun `shared text is captured without the clipboard`() = runTest {
        val accepted = repository.captureText("text from share sheet")

        assertTrue(accepted)
        assertEquals(listOf("text from share sheet"), writer.saved.map { it.text })
    }

    @Test
    fun `blank shared text is rejected`() = runTest {
        assertFalse(repository.captureText("   "))
        assertTrue(writer.saved.isEmpty())
    }

    @Test
    fun `capture current of empty clipboard does nothing`() = runTest {
        gateway.current = null

        val accepted = repository.captureCurrent()

        assertFalse(accepted)
        assertTrue(writer.saved.isEmpty())
    }

    private fun kotlinx.coroutines.test.TestScope.collectPackets(): List<SyncPacket> {
        val packets = mutableListOf<SyncPacket>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.outgoingPackets().collect { packets += it }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.run()
        }
        runCurrent()
        return packets
    }
}
