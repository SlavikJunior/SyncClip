package io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.slavikjunior.syncclip.core.datastore.AppSettings
import io.github.slavikjunior.syncclip.core.datastore.AppSettingsStore
import io.github.slavikjunior.syncclip.core.datastore.AutoStart
import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettingsStore
import io.github.slavikjunior.syncclip.core.datastore.HistoryLimitsRules
import io.github.slavikjunior.syncclip.core.datastore.NotificationSettings
import io.github.slavikjunior.syncclip.core.platform.BiometricAuthenticator
import io.github.slavikjunior.syncclip.core.platform.DocumentGateway
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.ArchiveExportOutcome
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.ArchiveFormat
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.ArchiveImportOutcome
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceKind
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceNameError
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.repository.DeviceRepository
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.repository.HistoryArchiveService
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.usecase.ValidateDeviceNameUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import syncclip.feature.settings.impl.generated.resources.Res
import syncclip.feature.settings.impl.generated.resources.settings_archive_empty
import syncclip.feature.settings.impl.generated.resources.settings_archive_exported
import syncclip.feature.settings.impl.generated.resources.settings_archive_exported_generic
import syncclip.feature.settings.impl.generated.resources.settings_archive_failed
import syncclip.feature.settings.impl.generated.resources.settings_archive_imported
import syncclip.feature.settings.impl.generated.resources.settings_archive_invalid
import syncclip.feature.settings.impl.generated.resources.settings_biometric_unavailable
import syncclip.feature.settings.impl.generated.resources.settings_error_invalid_name
import syncclip.feature.settings.impl.generated.resources.settings_error_load
import syncclip.feature.settings.impl.generated.resources.settings_limits_error
import syncclip.feature.settings.impl.generated.resources.settings_limits_saved
import syncclip.feature.settings.impl.generated.resources.settings_update_queued
import syncclip.feature.settings.impl.generated.resources.settings_update_saved

internal class DeviceManagementViewModel(
    private val repository: DeviceRepository,
    private val validateName: ValidateDeviceNameUseCase,
    private val autoStart: AutoStart? = null,
    private val settings: ClipboardSettingsStore? = null,
    private val appSettings: AppSettingsStore? = null,
    private val biometrics: BiometricAuthenticator? = null,
    private val documents: DocumentGateway? = null,
    private val archive: HistoryArchiveService? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(DeviceManagementUiState())
    val state: StateFlow<DeviceManagementUiState> = _state.asStateFlow()

    private val _effect = Channel<DeviceEffect>(Channel.BUFFERED)
    val effect: Flow<DeviceEffect> = _effect.receiveAsFlow()

    /** Один коллектор счётчика отложенных изменений на экран. */
    private var pendingObserved = false

    fun handleIntent(intent: DeviceIntent) {
        when (intent) {
            DeviceIntent.Load -> load()
            DeviceIntent.RetryPending -> retryPending()
            is DeviceIntent.OpenRename -> openRename(intent.deviceId)
            is DeviceIntent.NameChanged -> _state.update { it.copy(draftName = intent.name) }
            is DeviceIntent.IconSelected -> _state.update { it.copy(draftIcon = intent.icon) }
            DeviceIntent.Save -> save()
            DeviceIntent.DismissDialog -> _state.update { it.copy(editingDeviceId = null) }
            DeviceIntent.DismissError -> _state.update { it.copy(error = null) }
            DeviceIntent.ToggleAutoStart -> toggleAutoStart()
            is DeviceIntent.EntriesChanged -> _state.update {
                it.copy(maxEntriesInput = intent.value, limitsError = null)
            }

            is DeviceIntent.TtlHoursChanged -> _state.update {
                it.copy(ttlHoursInput = intent.value, limitsError = null)
            }

            is DeviceIntent.MaxBytesMbChanged -> _state.update {
                it.copy(maxBytesMbInput = intent.value, limitsError = null)
            }

            DeviceIntent.SaveHistoryLimits -> saveHistoryLimits()
            is DeviceIntent.ToggleNotification -> toggleNotification(intent.kind)
            DeviceIntent.ToggleAutoClearSensitive -> toggleAutoClearSensitive()
            DeviceIntent.ToggleBiometricLock -> toggleBiometricLock()
            is DeviceIntent.SetBiometricTimeout -> setBiometricTimeout(intent.minutes)
            DeviceIntent.OpenExportDialog -> _state.update { it.copy(exportDialogVisible = true) }
            DeviceIntent.DismissExportDialog -> _state.update { it.copy(exportDialogVisible = false) }
            is DeviceIntent.ExportHistory -> exportHistory(intent.format)
            DeviceIntent.ImportHistory -> importHistory()
        }
    }

    private fun saveHistoryLimits() {
        val store = settings ?: return
        val current = _state.value
        val entries = current.maxEntriesInput.trim().toIntOrNull()
        val ttlHours = current.ttlHoursInput.trim().toIntOrNull()
        // Мегабайты проверяем до умножения: `Long` переполняется, и абсурдное
        // значение (2^44 + 1 МБ) после переполнения могло бы попасть в
        // допустимый диапазон — пользователь получил бы случайный лимит.
        val maxBytesMb = current.maxBytesMbInput.trim().toLongOrNull()
            ?.takeIf { it in 1..MAX_MEGABYTES }
        val maxBytes = maxBytesMb?.times(MEBIBYTE)
        if (
            entries == null || ttlHours == null || maxBytes == null ||
            !HistoryLimitsRules.validate(entries, ttlHours, maxBytes)
        ) {
            _state.update { it.copy(limitsError = Res.string.settings_limits_error) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLimitsSaving = true) }
            // Сбой записи DataStore (диск заполнен, файл повреждён) — это
            // «не сохранилось», а не падение приложения: исключение из
            // viewModelScope уронило бы экран.
            val applied = runCatching { store.setHistoryLimits(entries, ttlHours, maxBytes) }
                .getOrDefault(false)
            _state.update {
                it.copy(
                    isLimitsSaving = false,
                    limitsError = if (applied) null else Res.string.settings_limits_error,
                )
            }
            if (applied) {
                _effect.send(DeviceEffect.ShowMessage(Res.string.settings_limits_saved))
            }
        }
    }

    private suspend fun loadHistoryLimits() {
        val store = settings ?: return
        val limits = store.current()
        _state.update {
            it.copy(
                maxEntriesInput = limits.maxEntries.toString(),
                ttlHoursInput = limits.ttlHours.toString(),
                maxBytesMbInput = (limits.maxBytes / MEBIBYTE).toString(),
            )
        }
    }

    private fun toggleAutoStart() {
        val controller = autoStart ?: return
        val target = !_state.value.autoStartEnabled
        viewModelScope.launch {
            _state.update { it.copy(isAutoStartBusy = true) }
            val applied = controller.setEnabled(target)
            _state.update {
                it.copy(
                    autoStartEnabled = if (applied) target else it.autoStartEnabled,
                    isAutoStartBusy = false,
                )
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            // Сбой фоновой отправки отложенных изменений не должен мешать
            // показать экран: загрузка каталога и лимитов важнее.
            runCatching { repository.flushPending() }
            loadAutoStart()
            loadHistoryLimits()
            loadAppPreferences()
            val result = repository.loadDevices()
            result.onSuccess { devices ->
                _state.update { it.copy(devices = devices, isLoading = false) }
            }.onFailure {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = Res.string.settings_error_load,
                    )
                }
            }
            observePending()
        }
    }

    /**
     * Подписка на счётчик отложенных изменений — одна на экран.
     *
     * `load()` вызывается и из «повторить»: без флага каждый вызов добавлял бы
     * ещё один коллектор Room-флоу, и они копились бы до закрытия экрана.
     */
    private fun observePending() {
        if (pendingObserved) return
        pendingObserved = true
        viewModelScope.launch {
            repository.observePendingCount()
                // Ошибка чтения счётчика — не повод падать: показываем прежнее.
                .catch { }
                .collect { count ->
                    _state.update { it.copy(pendingCount = count) }
                }
        }
    }

    private suspend fun loadAutoStart() {
        val controller = autoStart ?: return
        if (!controller.isSupported) return
        val enabled = controller.isEnabled()
        _state.update {
            it.copy(autoStartSupported = true, autoStartEnabled = enabled)
        }
    }

    private fun retryPending() {
        viewModelScope.launch {
            repository.flushPending()
            load()
        }
    }

    private fun openRename(deviceId: String) {
        val device = _state.value.devices.firstOrNull { it.id == deviceId } ?: return
        _state.update {
            it.copy(
                editingDeviceId = deviceId,
                draftName = device.name,
                draftIcon = device.icon,
            )
        }
    }

    private fun save() {
        viewModelScope.launch {
            val current = _state.value
            val id = current.editingDeviceId ?: return@launch
            val name = validateName(current.draftName)
            if (name.isFailure) {
                val message = when (name.exceptionOrNull()) {
                    is DeviceNameError -> Res.string.settings_error_invalid_name
                    else -> Res.string.settings_error_invalid_name
                }
                _state.update { it.copy(error = message) }
                return@launch
            }

            val result = repository.updateDevice(id, name.getOrNull(), current.draftIcon)
            result.onSuccess { updated ->
                _state.update { state ->
                    state.copy(
                        devices = state.devices.map { if (it.id == id) updated else it },
                        editingDeviceId = null,
                    )
                }
                _effect.send(DeviceEffect.ShowMessage(Res.string.settings_update_saved))
            }.onFailure {
                _state.update { it.copy(editingDeviceId = null) }
                _effect.send(DeviceEffect.ShowMessage(Res.string.settings_update_queued))
            }
        }
    }

    // ---- Phase 2: настройки Phase 2 (US-009, US-021, US-022, US-018, US-024) ----

    /**
     * Читает настройки интерфейса и приватности.
     *
     * Биометрия настраивается сразу при загрузке: после убийства процесса
     * платформенный наблюдатель не помнит, была ли блокировка включена.
     */
    private suspend fun loadAppPreferences() {
        val store = appSettings ?: return
        val current = runCatching { store.current() }.getOrNull() ?: AppSettings()
        val authenticator = biometrics
        _state.update {
            it.copy(
                notifications = current.notifications,
                autoClearSensitive = current.autoClearSensitive,
                biometricAvailable = authenticator?.isAvailable == true,
                biometricLock = current.security.biometricLock,
                biometricTimeoutMinutes = current.security.timeoutMinutes,
                documentsSupported = documents?.isSupported == true,
            )
        }
        runCatching {
            authenticator?.configure(
                enabled = current.security.biometricLock,
                timeoutMinutes = current.security.timeoutMinutes,
            )
        }
    }

    private fun toggleNotification(kind: NotificationKind) {
        val store = appSettings ?: return
        val current = _state.value.notifications
        val updated = when (kind) {
            NotificationKind.NEW_COPIES -> current.copy(newCopies = !current.newCopies)
            NotificationKind.NEW_DEVICE -> current.copy(newDevice = !current.newDevice)
            NotificationKind.FAILED_SIGN_IN -> current.copy(failedSignIn = !current.failedSignIn)
            NotificationKind.SOUND -> current.copy(sound = !current.sound)
            NotificationKind.VIBRATION -> current.copy(vibration = !current.vibration)
        }
        viewModelScope.launch {
            val applied = runCatching { store.setNotifications(updated) }.isSuccess
            if (applied) _state.update { it.copy(notifications = updated) }
        }
    }

    private fun toggleAutoClearSensitive() {
        val store = appSettings ?: return
        val target = !_state.value.autoClearSensitive
        viewModelScope.launch {
            val applied = runCatching { store.setAutoClearSensitive(target) }.isSuccess
            if (applied) _state.update { it.copy(autoClearSensitive = target) }
        }
    }

    private fun toggleBiometricLock() {
        val store = appSettings ?: return
        val authenticator = biometrics
        if (authenticator?.isAvailable != true) {
            viewModelScope.launch {
                _effect.send(
                    DeviceEffect.ShowMessage(Res.string.settings_biometric_unavailable),
                )
            }
            return
        }
        val target = !_state.value.biometricLock
        val timeout = _state.value.biometricTimeoutMinutes
        viewModelScope.launch {
            val applied = runCatching { store.setBiometricLock(target, timeout) }
                .getOrDefault(false)
            if (!applied) return@launch
            runCatching { authenticator.configure(target, timeout) }
            _state.update { it.copy(biometricLock = target) }
        }
    }

    private fun setBiometricTimeout(minutes: Int) {
        val store = appSettings ?: return
        val enabled = _state.value.biometricLock
        viewModelScope.launch {
            val applied = runCatching { store.setBiometricLock(enabled, minutes) }
                .getOrDefault(false)
            if (!applied) return@launch
            runCatching { biometrics?.configure(enabled, minutes) }
            _state.update { it.copy(biometricTimeoutMinutes = minutes) }
        }
    }

    private fun exportHistory(format: ArchiveFormat) {
        val service = archive ?: return
        viewModelScope.launch {
            _state.update { it.copy(exportDialogVisible = false, isArchiveBusy = true) }
            val outcome = runCatching { service.export(format) }
                .getOrElse { ArchiveExportOutcome.Failed }
            _state.update { it.copy(isArchiveBusy = false) }
            when (outcome) {
                is ArchiveExportOutcome.Saved -> {
                    val location = outcome.location
                    val effect = if (location.isNullOrBlank()) {
                        DeviceEffect.ShowMessage(Res.string.settings_archive_exported_generic)
                    } else {
                        DeviceEffect.ShowMessage(
                            Res.string.settings_archive_exported,
                            listOf(location),
                        )
                    }
                    _effect.send(effect)
                }

                ArchiveExportOutcome.Cancelled -> Unit
                ArchiveExportOutcome.Failed -> _effect.send(
                    DeviceEffect.ShowMessage(Res.string.settings_archive_failed),
                )
            }
        }
    }

    private fun importHistory() {
        val service = archive ?: return
        viewModelScope.launch {
            _state.update { it.copy(isArchiveBusy = true) }
            val outcome = runCatching { service.import() }
                .getOrElse { ArchiveImportOutcome.Failed }
            _state.update { it.copy(isArchiveBusy = false) }
            when (outcome) {
                is ArchiveImportOutcome.Imported -> _effect.send(
                    DeviceEffect.ShowMessage(
                        Res.string.settings_archive_imported,
                        listOf(outcome.imported, outcome.skipped),
                    ),
                )

                ArchiveImportOutcome.Cancelled -> Unit
                ArchiveImportOutcome.InvalidFormat -> _effect.send(
                    DeviceEffect.ShowMessage(Res.string.settings_archive_invalid),
                )

                ArchiveImportOutcome.Empty -> _effect.send(
                    DeviceEffect.ShowMessage(Res.string.settings_archive_empty),
                )

                ArchiveImportOutcome.Failed -> _effect.send(
                    DeviceEffect.ShowMessage(Res.string.settings_archive_failed),
                )
            }
        }
    }
}

private const val MEBIBYTE = 1024L * 1024L

/** Верхняя граница ввода в мегабайтах — совпадает с [HistoryLimitsRules.MAX_BYTES]. */
private const val MAX_MEGABYTES = 100L
