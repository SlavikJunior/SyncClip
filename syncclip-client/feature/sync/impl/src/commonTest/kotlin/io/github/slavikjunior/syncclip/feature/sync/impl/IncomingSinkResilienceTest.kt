package io.github.slavikjunior.syncclip.feature.sync.impl

import io.github.slavikjunior.syncclip.feature.sync.impl.data.repository.DefaultClipboardIncomingSink
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.IncomingClipboardAction
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.IncomingClipboardPacket
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardHistoryWriter
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.DeduplicateEventUseCase
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.ResolveIncomingClipboardUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Пограничные случаи обработки входящих пакетов.
 *
 * Пакет приходит из сети, то есть от недоверенной стороны: пустой текст,
 * метка времени из будущего, сбой локального хранилища. Ни один из этих
 * сценариев не должен рвать соединение и не должен терять уже принятый пакет.
 */
internal class IncomingSinkResilienceTest {

    private val gateway = FakeClipboardGateway()
    private val writer = FakeHistoryWriter()
    private val settings = FakeClipboardSettingsStore()
    private val clock = TestClock()
    private val registry = FakeProcessedEventRegistry()

    private fun sink(history: ClipboardHistoryWriter = writer) = DefaultClipboardIncomingSink(
        deduplicate = DeduplicateEventUseCase(registry),
        resolve = ResolveIncomingClipboardUseCase(),
        gateway = gateway,
        historyWriter = history,
        settings = settings,
        clock = clock,
    )

    private fun packet(
        text: String,
        timestampMillis: Long = clock.nowMillis() - 1_000,
        eventId: String = "event-1",
    ) = IncomingClipboardPacket(
        eventId = eventId,
        originDeviceId = "device-b",
        text = text,
        copiedAtMillis = timestampMillis,
    )

    @Test
    fun `blank packet is ignored and not stored`() = runTest {
        val action = sink().receive(packet(text = "   "))

        assertEquals(IncomingClipboardAction.IGNORE, action)
        assertTrue(writer.saved.isEmpty())
        assertTrue(gateway.inserted.isEmpty())
    }

    @Test
    fun `packet above the passport limit is ignored`() = runTest {
        val oversized = "a".repeat(512 * 1024 + 1)

        val action = sink().receive(packet(text = oversized))

        assertEquals(IncomingClipboardAction.IGNORE, action)
        assertTrue(writer.saved.isEmpty(), "лимит NFR действует и на приёме")
        assertTrue(gateway.inserted.isEmpty())
    }

    @Test
    fun `packet of exactly the limit size is accepted`() = runTest {
        val atLimit = "a".repeat(512 * 1024)

        val action = sink().receive(packet(text = atLimit))

        assertEquals(IncomingClipboardAction.SAVE_AND_INSERT, action)
        assertEquals(1, writer.saved.size)
    }

    @Test
    fun `future timestamp from a peer is clamped to now`() = runTest {
        val future = clock.nowMillis() + 365L * 24 * 60 * 60 * 1000

        sink().receive(packet(text = "from peer", timestampMillis = future))

        assertEquals(clock.nowMillis(), writer.saved.single().timestampMillis)
    }

    @Test
    fun `unknown timestamp is replaced with local time`() = runTest {
        sink().receive(packet(text = "from peer", timestampMillis = 0))

        assertEquals(clock.nowMillis(), writer.saved.single().timestampMillis)
    }

    @Test
    fun `slightly skewed timestamp is kept`() = runTest {
        val skewed = clock.nowMillis() + 60_000

        sink().receive(packet(text = "from peer", timestampMillis = skewed))

        assertEquals(skewed, writer.saved.single().timestampMillis)
    }

    @Test
    fun `history failure does not lose the packet`() = runTest {
        val failing = object : ClipboardHistoryWriter {
            override suspend fun save(text: String, timestampMillis: Long, sourceDeviceId: String?) {
                error("disk is full")
            }
        }

        // Исключение не должно выйти наружу: транспорт не отправит ACK и
        // переподключится, а пакет уже помечен обработанным.
        val action = sink(failing).receive(packet(text = "still delivered"))

        assertEquals(IncomingClipboardAction.SAVE_AND_INSERT, action)
        assertEquals(listOf("still delivered"), gateway.inserted)
    }

    @Test
    fun `duplicate packet is applied once`() = runTest {
        val local = sink()

        local.receive(packet(text = "once", eventId = "dup"))
        val second = local.receive(packet(text = "once", eventId = "dup"))

        assertEquals(IncomingClipboardAction.IGNORE, second)
        assertEquals(1, gateway.inserted.size)
        assertEquals(1, writer.saved.size)
    }

    @Test
    fun `offline packet is stored but not inserted`() = runTest {
        settings.setOnline(false)

        val action = sink().receive(packet(text = "queued"))

        assertEquals(IncomingClipboardAction.SAVE_ONLY, action)
        assertTrue(gateway.inserted.isEmpty())
        assertEquals(listOf("queued"), writer.saved.map { it.text })
    }
}
