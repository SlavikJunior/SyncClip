package io.github.slavikjunior.syncclip.feature.sync.impl

import io.github.slavikjunior.syncclip.core.clipboard.ClipboardCapture
import io.github.slavikjunior.syncclip.core.clipboard.PendingShareSource
import io.github.slavikjunior.syncclip.feature.sync.impl.data.repository.ClipboardCaptureRepository
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncPacket
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardHistoryWriter
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.AcceptLocalCaptureUseCase
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.DebounceClipboardUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Конвейер захвата при сбоях локального хранилища.
 *
 * Один неудачный `upsert` (диск полон, БД заблокирована) не должен навсегда
 * останавливать синхронизацию: следующее копирование обязано обработаться, а
 * пакет — уйти в сеть, потому что запись в историю нужна только локально.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ClipboardCaptureResilienceTest {

    private val gateway = FakeClipboardGateway()
    private val settings = FakeClipboardSettingsStore()
    private val clock = TestClock()

    @Test
    fun `failing history writer does not stop the pipeline`() = runTest {
        val writer = FlakyHistoryWriter(failures = 1)
        val repository = repository(writer, shares = FakePendingShareSource())
        val outgoing = collectPackets(repository)

        gateway.publish(ClipboardCapture("first", clock.nowMillis()))
        advanceTimeBy(150)
        runCurrent()

        gateway.publish(ClipboardCapture("second", clock.nowMillis()))
        advanceTimeBy(150)
        runCurrent()

        // Первая запись не сохранилась, но ушла в сеть; конвейер жив.
        assertEquals(listOf("first", "second"), outgoing.map { it.text })
        assertEquals(listOf("second"), writer.saved)
    }

    @Test
    fun `failing pending share queue does not stop the pipeline`() = runTest {
        val shares = object : PendingShareSource {
            override fun take(): List<String> = error("app group is unavailable")
        }
        val writer = FakeHistoryWriter()
        val repository = repository(writer, shares)
        val outgoing = collectPackets(repository)

        gateway.publish(ClipboardCapture("after failure", clock.nowMillis()))
        advanceTimeBy(150)
        runCurrent()

        assertEquals(listOf("after failure"), outgoing.map { it.text })
        assertEquals(listOf("after failure"), writer.saved.map { it.text })
    }

    @Test
    fun `capture from the share sheet survives a storage failure`() = runTest {
        val repository = repository(FlakyHistoryWriter(failures = Int.MAX_VALUE), FakePendingShareSource())

        val accepted = repository.captureText("shared while disk is full")

        assertTrue(accepted)
    }

    private fun repository(
        writer: ClipboardHistoryWriter,
        shares: PendingShareSource,
    ) = ClipboardCaptureRepository(
        gateway = gateway,
        pendingShares = shares,
        historyWriter = writer,
        settings = settings,
        accept = AcceptLocalCaptureUseCase(),
        debounce = DebounceClipboardUseCase(),
        clock = clock,
    )

    private fun TestScope.collectPackets(repository: ClipboardCaptureRepository): List<SyncPacket> {
        val packets = mutableListOf<SyncPacket>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.outgoingPackets().collect { packets += it }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.run() }
        runCurrent()
        return packets
    }
}

/** Пишет в историю, но первые [failures] вызовов падают. */
private class FlakyHistoryWriter(private var failures: Int) : ClipboardHistoryWriter {

    val saved: MutableList<String> = mutableListOf()

    override suspend fun save(text: String, timestampMillis: Long, sourceDeviceId: String?) {
        if (failures > 0) {
            failures--
            error("database is locked")
        }
        saved += text
    }
}
