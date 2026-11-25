package io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceNameError
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.repository.DeviceRepository
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.usecase.ValidateDeviceNameUseCase
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceKind
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import syncclip.feature.settings.impl.generated.resources.Res
import syncclip.feature.settings.impl.generated.resources.settings_error_invalid_name
import syncclip.feature.settings.impl.generated.resources.settings_error_load
import syncclip.feature.settings.impl.generated.resources.settings_update_queued
import syncclip.feature.settings.impl.generated.resources.settings_update_saved

internal class DeviceManagementViewModel(
    private val repository: DeviceRepository,
    private val validateName: ValidateDeviceNameUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(DeviceManagementUiState())
    val state: StateFlow<DeviceManagementUiState> = _state.asStateFlow()

    private val _effect = Channel<DeviceEffect>(Channel.BUFFERED)
    val effect: Flow<DeviceEffect> = _effect.receiveAsFlow()

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
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repository.flushPending()
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
            repository.observePendingCount().collect { count ->
                _state.update { it.copy(pendingCount = count) }
            }
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
