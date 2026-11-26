package io.github.slavikjunior.syncclip.core.clipboard

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class ObservedClipboardGatewayTest {

    private val clock = MutableEpochClock()

    private fun gateway(system: SystemClipboard) = createClipboardGateway(
        systemClipboard = system,
        clock = clock,
        echoWindowMillis = 2_000,
    )

    @Test
    fun `user copy is emitted once`() = runTest {
        val system = FakeSystemClipboard()
        val received = collectCopies(gateway(system))

        system.publish("hello")
        runCurrent()

        assertEquals(listOf("hello"), received.map { it.text })
    }

    @Test
    fun `blank clipboard events are ignored`() = runTest {
        val system = FakeSystemClipboard()
        val received = collectCopies(gateway(system))

        system.publish("   ")
        system.publish("")
        runCurrent()

        assertTrue(received.isEmpty())
    }

    @Test
    fun `text above the passport limit is ignored`() = runTest {
        val system = FakeSystemClipboard()
        val received = collectCopies(gateway(system))
        val oversized = "a".repeat(ClipboardLimits.MAX_TEXT_BYTES + 1)

        system.publish(oversized)
        runCurrent()

        assertTrue(received.isEmpty())
    }

    @Test
    fun `text of exactly the limit size is accepted`() = runTest {
        val system = FakeSystemClipboard()
        val received = collectCopies(gateway(system))
        val limit = "a".repeat(ClipboardLimits.MAX_TEXT_BYTES)

        system.publish(limit)
        runCurrent()

        assertEquals(1, received.size)
    }

    @Test
    fun `repeated identical text does not produce a second event`() = runTest {
        val system = FakeSystemClipboard()
        val received = collectCopies(gateway(system))

        system.publish("hello")
        system.publish("hello")
        runCurrent()

        assertEquals(1, received.size)
    }

    @Test
    fun `programmatic insertion is written and its echo is ignored`() = runTest {
        val system = FakeSystemClipboard()
        val gateway = gateway(system)
        val received = collectCopies(gateway)

        gateway.insert("remote text")
        system.publish("remote text")
        runCurrent()

        assertEquals(listOf("remote text"), system.written)
        assertTrue(received.isEmpty(), "эхо собственной вставки не должно уходить в сеть")
    }

    @Test
    fun `same text after the echo window is a real copy`() = runTest {
        val system = FakeSystemClipboard()
        val gateway = gateway(system)
        val received = collectCopies(gateway)

        gateway.insert("remote text")
        clock.advance(2_001)
        system.publish("remote text")
        runCurrent()

        assertEquals(listOf("remote text"), received.map { it.text })
    }

    @Test
    fun `insert of blank text does not touch the clipboard`() = runTest {
        val system = FakeSystemClipboard()
        val gateway = gateway(system)

        gateway.insert("  ")

        assertTrue(system.written.isEmpty())
    }

    @Test
    fun `last local capture time advances only on accepted copies`() = runTest {
        val system = FakeSystemClipboard()
        val gateway = gateway(system)
        collectCopies(gateway)
        clock.advance(5_000)

        system.publish("hello")
        runCurrent()
        system.publish("   ")
        runCurrent()

        assertEquals(clock.nowMillis(), gateway.lastLocalCaptureMillis())
    }

    private fun kotlinx.coroutines.test.TestScope.collectCopies(
        gateway: ClipboardGateway,
    ): List<ClipboardCapture> {
        val received = mutableListOf<ClipboardCapture>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            gateway.localCopies().collect { received += it }
        }
        runCurrent()
        return received
    }
}

/** Часы, которые двигает тест. */
private class MutableEpochClock(startMillis: Long = 1_000_000L) : EpochClock {

    private var currentMillis: Long = startMillis

    override fun nowMillis(): Long = currentMillis

    fun advance(deltaMillis: Long) {
        currentMillis += deltaMillis
    }
}

/** Буфер, которым управляет тест: события публикуются вручную. */
private class FakeSystemClipboard : SystemClipboard {

    private val events = MutableSharedFlow<ClipboardChange>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    val written: MutableList<String> = mutableListOf()

    override fun changes(): Flow<ClipboardChange> = events

    override suspend fun read(): String? = written.lastOrNull()

    override suspend fun write(text: String) {
        written += text
    }

    suspend fun publish(text: String) {
        events.emit(ClipboardChange(text = text, changedAtMillis = 0L))
    }
}
