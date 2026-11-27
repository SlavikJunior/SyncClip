package io.github.slavikjunior.syncclip.feature.sync.impl

import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.DebounceClipboardUseCase
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncPacket
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class DebounceClipboardUseCaseTest {

    private val useCase = DebounceClipboardUseCase()

    @Test
    fun `rapid copies produce only the last packet`() = runTest {
        // Arrange
        val emitted = mutableListOf<String>()
        val source = flow {
            repeat(5) { index ->
                emit(SyncPacket(eventId = "event-$index", text = "Text $index", copiedAtMillis = index.toLong()))
                kotlinx.coroutines.delay(30)
            }
        }

        // Act
        val collector = launch { useCase(source).collect { emitted += it.text } }
        kotlinx.coroutines.delay(200)
        collector.cancel()

        // Assert
        assertEquals(listOf("Text 4"), emitted)
    }

    @Test
    fun `programmatic paste is ignored`() = runTest {
        // Arrange
        val emitted = mutableListOf<String>()
        val source = flow {
            emit(SyncPacket(eventId = "event-injected", text = "injected", copiedAtMillis = 0L, isProgrammatic = true))
            emit(SyncPacket(eventId = "event-user", text = "user copy", copiedAtMillis = 1L))
        }

        // Act
        val collector = launch { useCase(source).collect { emitted += it.text } }
        kotlinx.coroutines.delay(200)
        collector.cancel()

        // Assert
        assertEquals(listOf("user copy"), emitted)
    }
}
