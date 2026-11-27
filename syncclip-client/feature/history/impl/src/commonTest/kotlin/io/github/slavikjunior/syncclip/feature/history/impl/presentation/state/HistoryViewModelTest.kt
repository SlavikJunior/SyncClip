package io.github.slavikjunior.syncclip.feature.history.impl.presentation.state

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Поведение кэша первого экрана: снимок показывается до ответа Room и не
 * перезаписывает свежую страницу, если ответ пришёл раньше.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class HistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cached page is shown while Room has not emitted`() = runTest(dispatcher) {
        val viewModel = HistoryViewModel(
            repository = FakeHistoryRepository(
                cached = listOf(historyEntry("cached")),
                pageFlow = { flow { awaitCancellation() } },
            ),
            deviceDirectory = null,
        )

        advanceUntilIdle()

        assertEquals(listOf("cached"), viewModel.state.value.entries.map { it.id })
        assertEquals(1, viewModel.state.value.totalCount, "счётчик не должен показывать «N из 0»")
    }

    @Test
    fun `fresh Room page replaces cached page`() = runTest(dispatcher) {
        val viewModel = HistoryViewModel(
            repository = FakeHistoryRepository(
                cached = listOf(historyEntry("cached")),
                page = listOf(historyEntry("fresh")),
            ),
            deviceDirectory = null,
        )

        advanceUntilIdle()

        assertEquals(listOf("fresh"), viewModel.state.value.entries.map { it.id })
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `cached page is not restored after Room emitted`() = runTest(dispatcher) {
        // Room отвечает сразу, а чтение кэша завершается позже: снимок не должен
        // лечь поверх уже отрисованной страницы.
        val gate = CompletableDeferred<Unit>()
        val viewModel = HistoryViewModel(
            repository = FakeHistoryRepository(
                cached = listOf(historyEntry("cached")),
                page = listOf(historyEntry("fresh")),
                cachedGate = gate,
            ),
            deviceDirectory = null,
        )

        advanceUntilIdle()
        assertEquals(listOf("fresh"), viewModel.state.value.entries.map { it.id })

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("fresh"), viewModel.state.value.entries.map { it.id })
    }

    @Test
    fun `eviction uses limits from settings`() = runTest(dispatcher) {
        val repository = FakeHistoryRepository(page = emptyList())
        val viewModel = HistoryViewModel(
            repository = repository,
            deviceDirectory = null,
            settings = FixedSettings(
                io.github.slavikjunior.syncclip.core.datastore.ClipboardSettings(
                    maxEntries = 42,
                    ttlHours = 3,
                ),
            ),
        )
        val before = kotlin.time.Clock.System.now().toEpochMilliseconds()

        viewModel.handleIntent(HistoryIntent.Load)
        advanceUntilIdle()

        assertEquals(42, repository.lastEvictionMaxCount)
        val cutoff = requireNotNull(repository.lastEvictionCutoff)
        val expected = before - 3L * 60 * 60 * 1000
        assertTrue(cutoff in (expected - 5_000)..(expected + 60_000))
    }

    private class FixedSettings(
        private val value: io.github.slavikjunior.syncclip.core.datastore.ClipboardSettings,
    ) : io.github.slavikjunior.syncclip.core.datastore.ClipboardSettingsStore {

        override val state = kotlinx.coroutines.flow.flowOf(value)
        override suspend fun current() = value
        override suspend fun setOnline(isOnline: Boolean) = Unit
        override suspend fun setAutoInsert(autoInsert: Boolean) = Unit
        override suspend fun setMaxEntries(value: Int) = true
        override suspend fun setTtlHours(value: Int) = true
        override suspend fun setMaxBytes(value: Long) = true
        override suspend fun setHistoryLimits(
            maxEntries: Int,
            ttlHours: Int,
            maxBytes: Long,
        ) = true
    }
}
