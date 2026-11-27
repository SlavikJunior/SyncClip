package io.github.slavikjunior.syncclip.core.clipboard

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Защита от эха и дедупликация на границах окна (FR-2.7, FR-2.5).
 *
 * Системный callback приходит с задержкой и из другого потока, часы могут
 * прыгнуть назад, а текст — оказаться больше лимита. Правила должны оставаться
 * предсказуемыми: пользовательская копия важнее любой из этих аномалий.
 */
internal class ClipboardGatewayEdgeCasesTest {

    private val clipboard = ScriptedClipboard()
    private val clock = ScriptedClock()

    private fun gateway(echoWindowMillis: Long = 2_000L) = createClipboardGateway(
        systemClipboard = clipboard,
        clock = clock,
        echoWindowMillis = echoWindowMillis,
        maxTextBytes = 16,
    )

    @Test
    fun `own insertion echo is ignored exactly at the window boundary`() = runTest {
        val gateway = gateway()
        val copies = collectCopies(gateway)

        gateway.insert("hello")
        clock.advance(2_000)
        clipboard.publish("hello")
        runCurrent()

        assertTrue(copies.isEmpty(), "на границе окна событие всё ещё эхо")
    }

    @Test
    fun `copy one millisecond after the window is accepted`() = runTest {
        val gateway = gateway()
        val copies = collectCopies(gateway)

        gateway.insert("hello")
        clock.advance(2_001)
        clipboard.publish("hello")
        runCurrent()

        assertEquals(listOf("hello"), copies.map { it.text })
    }

    @Test
    fun `echo mark is consumed by the first event only`() = runTest {
        val gateway = gateway()
        val copies = collectCopies(gateway)

        gateway.insert("hello")
        clipboard.publish("hello")
        runCurrent()
        // Второе событие с тем же текстом — уже осознанное копирование.
        clipboard.publish("hello")
        runCurrent()

        assertEquals(1, copies.size, "метка эха одноразовая")
    }

    @Test
    fun `clock jumping backwards treats the event as an echo`() = runTest {
        val gateway = gateway()
        val copies = collectCopies(gateway)

        gateway.insert("hello")
        clock.rewind(60_000)
        clipboard.publish("hello")
        runCurrent()

        assertTrue(copies.isEmpty(), "отрицательная разница — тоже эхо")
    }

    @Test
    fun `insertion above the limit is ignored by the pipeline`() = runTest {
        val gateway = gateway()
        val copies = collectCopies(gateway)

        // В буфер записываем только то, что прошло лимит на приёме.
        gateway.insert("x".repeat(17))
        clipboard.publish("x".repeat(17))
        runCurrent()

        assertTrue(copies.isEmpty())
    }

    @Test
    fun `oversized text is not reported as a local copy`() = runTest {
        val gateway = gateway()
        val copies = collectCopies(gateway)

        clipboard.publish("x".repeat(17))
        runCurrent()

        assertTrue(copies.isEmpty())
        assertEquals(0L, gateway.lastLocalCaptureMillis())
    }

    @Test
    fun `blank insertion does not touch the clipboard`() = runTest {
        val gateway = gateway()

        gateway.insert("   ")

        assertTrue(clipboard.writes.isEmpty())
        assertNull(clipboard.read())
    }

    @Test
    fun `local copy timestamp advances only for accepted events`() = runTest {
        val gateway = gateway()
        collectCopies(gateway)

        clock.advance(100)
        clipboard.publish("ok")
        runCurrent()
        val afterFirst = gateway.lastLocalCaptureMillis()

        clock.advance(100)
        clipboard.publish("ok")
        runCurrent()

        assertEquals(afterFirst, gateway.lastLocalCaptureMillis())
    }

    @Test
    fun `failed insertion leaves no echo mark behind`() = runTest {
        val gateway = gateway()
        val copies = collectCopies(gateway)
        clipboard.failNextWrite = true

        val failure = runCatching { gateway.insert("hello") }
        assertTrue(failure.isFailure, "ошибка записи уходит вызывающему")

        // Буфер не изменился, значит следующее событие с этим же текстом —
        // осознанное копирование пользователя, а не эхо неудавшейся вставки.
        clipboard.publish("hello")
        runCurrent()

        assertEquals(listOf("hello"), copies.map { it.text })
    }

    private fun kotlinx.coroutines.test.TestScope.collectCopies(
        gateway: ClipboardGateway,
    ): List<ClipboardCapture> {
        val copies = mutableListOf<ClipboardCapture>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            gateway.localCopies().toList(copies)
        }
        runCurrent()
        return copies
    }
}

private class ScriptedClipboard : SystemClipboard {

    private val events = MutableSharedFlow<ClipboardChange>(extraBufferCapacity = 16)
    private val written = mutableListOf<String>()

    val writes: List<String> get() = written

    /** Имитация отказа записи (Android без фокуса, headless Desktop). */
    var failNextWrite: Boolean = false

    override fun changes(): Flow<ClipboardChange> = events

    override suspend fun read(): String? = written.lastOrNull()

    override suspend fun write(text: String) {
        if (failNextWrite) {
            failNextWrite = false
            throw IllegalStateException("clipboard is not available")
        }
        written += text
    }

    suspend fun publish(text: String) {
        events.emit(ClipboardChange(text = text, changedAtMillis = 0))
    }
}

private class ScriptedClock(private var currentMillis: Long = 1_000_000) : EpochClock {

    override fun nowMillis(): Long = currentMillis

    fun advance(deltaMillis: Long) {
        currentMillis += deltaMillis
    }

    fun rewind(deltaMillis: Long) {
        currentMillis -= deltaMillis
    }
}
