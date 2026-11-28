package io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state

import io.github.slavikjunior.syncclip.core.datastore.AppSettings
import io.github.slavikjunior.syncclip.core.datastore.AppSettingsStore
import io.github.slavikjunior.syncclip.core.datastore.NotificationSettings
import io.github.slavikjunior.syncclip.core.datastore.SecuritySettings
import io.github.slavikjunior.syncclip.core.platform.BiometricAuthenticator
import io.github.slavikjunior.syncclip.core.platform.DocumentGateway
import io.github.slavikjunior.syncclip.core.platform.FileOpenResult
import io.github.slavikjunior.syncclip.core.platform.FileSaveResult
import io.github.slavikjunior.syncclip.feature.settings.impl.data.repository.FakeDeviceRepository
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.ArchiveExportOutcome
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.ArchiveFormat
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.ArchiveImportOutcome
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.repository.HistoryArchiveService
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.usecase.ValidateDeviceNameUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** US-009, US-021, US-022, US-018: настройки Phase 2 на экране настроек. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class PhaseTwoSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val appSettings = FakeAppSettingsStore()
    private val biometrics = FakeBiometricAuthenticator(available = true)
    private val archive = FakeArchiveService()
    private val documents = FakeDocumentGateway()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        biometrics: FakeBiometricAuthenticator = this.biometrics,
        archive: FakeArchiveService = this.archive,
    ) = DeviceManagementViewModel(
        repository = FakeDeviceRepository(),
        validateName = ValidateDeviceNameUseCase(),
        autoStart = null,
        settings = null,
        appSettings = appSettings,
        biometrics = biometrics,
        documents = documents,
        archive = archive,
    )

    @Test
    fun `notification toggles are persisted one by one`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.handleIntent(DeviceIntent.Load)
        advanceUntilIdle()

        viewModel.handleIntent(DeviceIntent.ToggleNotification(NotificationKind.NEW_COPIES))
        advanceUntilIdle()
        assertTrue(appSettings.current().notifications.newCopies)

        viewModel.handleIntent(DeviceIntent.ToggleNotification(NotificationKind.SOUND))
        advanceUntilIdle()
        assertFalse(appSettings.current().notifications.sound)
        // Остальные флаги не тронуты.
        assertTrue(appSettings.current().notifications.vibration)
    }

    @Test
    fun `auto cleanup toggle is persisted`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.handleIntent(DeviceIntent.Load)
        advanceUntilIdle()

        viewModel.handleIntent(DeviceIntent.ToggleAutoClearSensitive)
        advanceUntilIdle()

        assertTrue(appSettings.current().autoClearSensitive)
        assertTrue(viewModel.state.value.autoClearSensitive)
    }

    @Test
    fun `biometric toggle is applied to the platform`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.handleIntent(DeviceIntent.Load)
        advanceUntilIdle()

        viewModel.handleIntent(DeviceIntent.ToggleBiometricLock)
        advanceUntilIdle()

        assertTrue(appSettings.current().security.biometricLock)
        assertEquals(5, appSettings.current().security.timeoutMinutes)
        assertEquals(true to 5, biometrics.configured.last())
    }

    @Test
    fun `biometric toggle without hardware changes nothing`() = runTest(dispatcher) {
        val unavailable = FakeBiometricAuthenticator(available = false)
        val viewModel = viewModel(biometrics = unavailable)
        viewModel.handleIntent(DeviceIntent.Load)
        advanceUntilIdle()

        viewModel.handleIntent(DeviceIntent.ToggleBiometricLock)
        advanceUntilIdle()

        assertFalse(appSettings.current().security.biometricLock)
        assertFalse(viewModel.state.value.biometricAvailable)
    }

    @Test
    fun `export opens the format dialog and calls the service`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.handleIntent(DeviceIntent.Load)
        advanceUntilIdle()

        viewModel.handleIntent(DeviceIntent.OpenExportDialog)
        assertTrue(viewModel.state.value.exportDialogVisible)

        viewModel.handleIntent(DeviceIntent.ExportHistory(ArchiveFormat.CSV))
        advanceUntilIdle()

        assertEquals(listOf(ArchiveFormat.CSV), archive.exported)
        assertFalse(viewModel.state.value.exportDialogVisible)
        assertFalse(viewModel.state.value.isArchiveBusy)
    }

    @Test
    fun `import reports counts and failure separately`() = runTest(dispatcher) {
        archive.importOutcome = ArchiveImportOutcome.Imported(imported = 150, skipped = 5)
        val viewModel = viewModel()
        viewModel.handleIntent(DeviceIntent.Load)
        advanceUntilIdle()

        viewModel.handleIntent(DeviceIntent.ImportHistory)
        advanceUntilIdle()
        assertEquals(1, archive.imports)

        archive.importOutcome = ArchiveImportOutcome.InvalidFormat
        viewModel.handleIntent(DeviceIntent.ImportHistory)
        advanceUntilIdle()
        assertEquals(2, archive.imports)
        assertFalse(viewModel.state.value.isArchiveBusy)
    }

    @Test
    fun `documents support decides whether archive buttons are shown`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.handleIntent(DeviceIntent.Load)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.documentsSupported)
    }

    @Test
    fun `biometric timeout is persisted`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.handleIntent(DeviceIntent.Load)
        advanceUntilIdle()
        viewModel.handleIntent(DeviceIntent.ToggleBiometricLock)
        advanceUntilIdle()

        viewModel.handleIntent(DeviceIntent.SetBiometricTimeout(0))
        advanceUntilIdle()

        assertEquals(0, appSettings.current().security.timeoutMinutes)
        assertEquals(true to 0, biometrics.configured.last())
        assertNull(viewModel.state.value.error)
    }
}

/** Хранилище настроек Phase 2 в памяти. */
private class FakeAppSettingsStore : AppSettingsStore {

    private val values = MutableStateFlow(AppSettings())
    override val state: Flow<AppSettings> = values

    override suspend fun current(): AppSettings = values.value

    override suspend fun setTheme(mode: String, preset: String): Boolean = true

    override suspend fun setRandomThemeSeed(seed: Long) = Unit

    override suspend fun setFontScale(scale: Float): Boolean = true

    override suspend fun setNotifications(settings: NotificationSettings) {
        values.value = values.value.copy(notifications = settings)
    }

    override suspend fun setAutoClearSensitive(enabled: Boolean) {
        values.value = values.value.copy(autoClearSensitive = enabled)
    }

    override suspend fun setBiometricLock(enabled: Boolean, timeoutMinutes: Int): Boolean {
        values.value = values.value.copy(
            security = SecuritySettings(biometricLock = enabled, timeoutMinutes = timeoutMinutes),
        )
        return true
    }
}

private class FakeBiometricAuthenticator(private val available: Boolean) : BiometricAuthenticator {

    val configured: MutableList<Pair<Boolean, Int>> = mutableListOf()

    override val isAvailable: Boolean = available
    override val locked: StateFlow<Boolean> = MutableStateFlow(false)

    override fun configure(enabled: Boolean, timeoutMinutes: Int) {
        configured += enabled to timeoutMinutes
    }

    override suspend fun authenticate(
        title: String,
        subtitle: String,
        cancelLabel: String,
    ): Boolean = false

    override fun lock() = Unit
}

private class FakeDocumentGateway : DocumentGateway {
    override val isSupported: Boolean = true

    override suspend fun save(fileName: String, content: String, mimeType: String): FileSaveResult =
        FileSaveResult.Saved(fileName)

    override suspend fun open(mimeTypes: List<String>): FileOpenResult =
        FileOpenResult.Cancelled
}

private class FakeArchiveService : HistoryArchiveService {

    val exported: MutableList<ArchiveFormat> = mutableListOf()
    var imports: Int = 0
    var importOutcome: ArchiveImportOutcome = ArchiveImportOutcome.Imported(0, 0)

    override suspend fun export(format: ArchiveFormat): ArchiveExportOutcome {
        exported += format
        return ArchiveExportOutcome.Saved(location = "Downloads/syncclip_export.csv")
    }

    override suspend fun import(): ArchiveImportOutcome {
        imports++
        return importOutcome
    }
}
