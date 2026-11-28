package io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.slavikjunior.syncclip.core.datastore.AutoStart
import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettingsStore
import io.github.slavikjunior.syncclip.core.datastore.HistoryLimitsRules
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceNameError
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.repository.DeviceRepository
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.usecase.ValidateDeviceNameUseCase
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceKind
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
}

private const val MEBIBYTE = 1024L * 1024L

/** Верхняя граница ввода в мегабайтах — совпадает с [HistoryLimitsRules.MAX_BYTES]. */
private const val MAX_MEGABYTES = 100L
