package io.github.slavikjunior.syncclip.feature.sync.impl

import io.github.slavikjunior.syncclip.feature.sync.impl.data.repository.DefaultClipboardIncomingSink
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.IncomingClipboardAction
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.IncomingClipboardPacket
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.DeduplicateEventUseCase
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.ResolveIncomingClipboardUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class DefaultClipboardIncomingSinkTest {

    private val gateway = FakeClipboardGateway()
    private val writer = FakeHistoryWriter()
    private val settings = FakeClipboardSettingsStore()
    private val clock = TestClock()

    private val sink = DefaultClipboardIncomingSink(
        deduplicate = DeduplicateEventUseCase(FakeProcessedEventRegistry()),
        resolve = ResolveIncomingClipboardUseCase(),
        gateway = gateway,
        historyWriter = writer,
        settings = settings,
        clock = clock,
    )

    @Test
    fun `new packet is saved and inserted`() = runTest {
        val action = sink.receive(packet("event-1"))

        assertEquals(IncomingClipboardAction.SAVE_AND_INSERT, action)
        assertEquals(listOf("remote text"), writer.saved.map { it.text })
        assertEquals("device-b", writer.saved.single().sourceDeviceId)
        assertEquals(listOf("remote text"), gateway.inserted)
    }

    @Test
    fun `duplicate delivery is ignored without side effects`() = runTest {
        sink.receive(packet("event-1"))
        val second = sink.receive(packet("event-1"))

        assertEquals(IncomingClipboardAction.IGNORE, second)
        assertEquals(1, writer.saved.size)
        assertEquals(1, gateway.inserted.size)
    }

    @Test
    fun `auto insert off keeps system clipboard untouched`() = runTest {
        settings.setAutoInsert(false)

        val action = sink.receive(packet("event-2"))

        assertEquals(IncomingClipboardAction.SAVE_ONLY, action)
        assertTrue(gateway.inserted.isEmpty())
        assertEquals(1, writer.saved.size)
    }

    @Test
    fun `recent local copy blocks insertion`() = runTest {
        gateway.publish(io.github.slavikjunior.syncclip.core.clipboard.ClipboardCapture("local", clock.nowMillis()))
        clock.advance(100)

        val action = sink.receive(packet("event-3"))

        assertEquals(IncomingClipboardAction.SAVE_ONLY, action)
        assertTrue(gateway.inserted.isEmpty())
    }

    private fun packet(eventId: String) = IncomingClipboardPacket(
        eventId = eventId,
        originDeviceId = "device-b",
        text = "remote text",
        copiedAtMillis = clock.nowMillis(),
    )
}
