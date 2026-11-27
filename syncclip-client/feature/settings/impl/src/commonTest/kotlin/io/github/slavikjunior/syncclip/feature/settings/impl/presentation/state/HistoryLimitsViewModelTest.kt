package io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state

import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettings
import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettingsStore
import io.github.slavikjunior.syncclip.feature.settings.impl.data.repository.FakeDeviceRepository
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.usecase.ValidateDeviceNameUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** FR-4.3: поля лимитов истории грузятся из хранилища и сохраняются с валидацией. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class HistoryLimitsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeSettingsStore(
        ClipboardSettings(maxEntries = 50, ttlHours = 48, maxBytes = 5L * 1024 * 1024),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = DeviceManagementViewModel(
        repository = FakeDeviceRepository(),
        validateName = ValidateDeviceNameUseCase(),
        autoStart = null,
        settings = settings,
    )

    @Test
    fun `limits are loaded into the form`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.handleIntent(DeviceIntent.Load)
        advanceUntilIdle()

        assertEquals("50", viewModel.state.value.maxEntriesInput)
        assertEquals("48", viewModel.state.value.ttlHoursInput)
        assertEquals("5", viewModel.state.value.maxBytesMbInput)
    }

    @Test
    fun `valid limits are persisted and confirmed`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.handleIntent(DeviceIntent.Load)
        advanceUntilIdle()

        viewModel.handleIntent(DeviceIntent.EntriesChanged("100"))
        viewModel.handleIntent(DeviceIntent.TtlHoursChanged("720"))
        viewModel.handleIntent(DeviceIntent.MaxBytesMbChanged("20"))
        viewModel.handleIntent(DeviceIntent.SaveHistoryLimits)
        advanceUntilIdle()

        val saved = settings.current()
        assertEquals(100, saved.maxEntries)
        assertEquals(720, saved.ttlHours)
        assertEquals(20L * 1024 * 1024, saved.maxBytes)
        assertNull(viewModel.state.value.limitsError)

        val effect = viewModel.effect.first()
        assertTrue(effect is DeviceEffect.ShowMessage, "после сохранения показываем подтверждение")
    }

    @Test
    fun `out of range values are rejected without writing`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.handleIntent(DeviceIntent.Load)
        advanceUntilIdle()

        viewModel.handleIntent(DeviceIntent.EntriesChanged("4"))
        viewModel.handleIntent(DeviceIntent.SaveHistoryLimits)
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.limitsError)
        assertEquals(50, settings.current().maxEntries)
    }

    @Test
    fun `non numeric input is cleared by the field and rejected`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.handleIntent(DeviceIntent.Load)
        advanceUntilIdle()

        viewModel.handleIntent(DeviceIntent.EntriesChanged(""))
        viewModel.handleIntent(DeviceIntent.SaveHistoryLimits)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.limitsError != null)
    }

    private class FakeSettingsStore(initial: ClipboardSettings) : ClipboardSettingsStore {

        private val values = MutableStateFlow(initial)
        override val state: Flow<ClipboardSettings> = values

        override suspend fun current(): ClipboardSettings = values.value

        override suspend fun setOnline(isOnline: Boolean) {
            values.update { it.copy(isOnline = isOnline) }
        }

        override suspend fun setAutoInsert(autoInsert: Boolean) {
            values.update { it.copy(autoInsert = autoInsert) }
        }

        override suspend fun setMaxEntries(value: Int): Boolean {
            values.update { it.copy(maxEntries = value) }
            return true
        }

        override suspend fun setTtlHours(value: Int): Boolean {
            values.update { it.copy(ttlHours = value) }
            return true
        }

        override suspend fun setMaxBytes(value: Long): Boolean {
            values.update { it.copy(maxBytes = value) }
            return true
        }

        override suspend fun setHistoryLimits(
            maxEntries: Int,
            ttlHours: Int,
            maxBytes: Long,
        ): Boolean {
            values.update {
                it.copy(maxEntries = maxEntries, ttlHours = ttlHours, maxBytes = maxBytes)
            }
            return true
        }
    }
}
