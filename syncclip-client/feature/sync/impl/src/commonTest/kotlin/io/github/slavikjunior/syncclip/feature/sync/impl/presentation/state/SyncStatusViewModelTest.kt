package io.github.slavikjunior.syncclip.feature.sync.impl.presentation.state

import io.github.slavikjunior.syncclip.feature.sync.impl.FakeClipboardGateway
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeClipboardSettingsStore
import io.github.slavikjunior.syncclip.feature.sync.impl.FakeHistoryWriter
import io.github.slavikjunior.syncclip.feature.sync.impl.FakePendingShareSource
import io.github.slavikjunior.syncclip.feature.sync.impl.TestClock
import io.github.slavikjunior.syncclip.feature.sync.impl.data.repository.ClipboardCaptureRepository
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncConnectionStatus
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.AcceptLocalCaptureUseCase
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.DebounceClipboardUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalCoroutinesApi::class)
internal class SyncStatusViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeClipboardSettingsStore()
    private val gateway = FakeClipboardGateway()
    private val repository = ClipboardCaptureRepository(
        gateway = gateway,
        pendingShares = FakePendingShareSource(),
        historyWriter = FakeHistoryWriter(),
        settings = settings,
        accept = AcceptLocalCaptureUseCase(),
        debounce = DebounceClipboardUseCase(),
        clock = TestClock(),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        connection: StateFlow<SyncConnectionStatus>? = null,
    ): SyncStatusViewModel = SyncStatusViewModel(
        captures = repository,
        packets = repository,
        settings = settings,
        starter = repository,
        deviceDirectory = null,
        connection = connection,
    )

    @Test
    fun `online toggle is persisted and reflected in state`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.handleIntent(SyncIntent.ToggleOnline)
        advanceUntilIdle()

        assertFalse(settings.current().isOnline)
        assertFalse(viewModel.state.value.isOnline)
    }

    @Test
    fun `auto insert toggle is persisted`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.handleIntent(SyncIntent.ToggleAutoInsert)
        advanceUntilIdle()

        assertFalse(settings.current().autoInsert)
        assertFalse(viewModel.state.value.autoInsert)
    }

    @Test
    fun `manual capture updates last text`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        gateway.current = "captured by hand"

        viewModel.handleIntent(SyncIntent.CaptureCurrent)
        advanceUntilIdle()

        assertEquals("captured by hand", viewModel.state.value.lastText)
    }

    @Test
    fun `offline toggle emits pause effect`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        var paused = false
        val collector = launch(dispatcher) {
            viewModel.effect.collect { paused = it is SyncEffect.SyncPaused }
        }

        viewModel.handleIntent(SyncIntent.ToggleOnline)
        advanceUntilIdle()

        assertTrue(paused)
        collector.cancel()
    }

    @Test
    fun `transport suspension is shown in state`() = runTest(dispatcher) {
        val connection = MutableStateFlow(SyncConnectionStatus.ONLINE)
        val viewModel = createViewModel(connection)
        advanceUntilIdle()
        assertEquals(SyncConnectionStatus.ONLINE, viewModel.state.value.connectionStatus)

        connection.value = SyncConnectionStatus.SUSPENDED
        advanceUntilIdle()

        assertEquals(SyncConnectionStatus.SUSPENDED, viewModel.state.value.connectionStatus)
    }

    @Test
    fun `settings write failure does not crash and state stays consistent`() = runTest(dispatcher) {
        settings.failWrites = true
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Сбой записи не должен выбрасывать исключение в viewModelScope.
        viewModel.handleIntent(SyncIntent.ToggleOnline)
        viewModel.handleIntent(SyncIntent.ToggleAutoInsert)
        advanceUntilIdle()

        // Store не изменился — экран показывает реальное состояние, а не нажатие.
        assertTrue(settings.current().isOnline)
        assertTrue(viewModel.state.value.isOnline)
    }
}
