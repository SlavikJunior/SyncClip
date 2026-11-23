package io.github.slavikjunior.syncclip.feature.sync.impl.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardPacketSource
import io.github.slavikjunior.syncclip.shared.model.DeviceDirectory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class SyncStatusViewModel(
    private val repository: ClipboardPacketSource,
    private val deviceDirectory: DeviceDirectory? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(SyncStatusUiState())
    val state: StateFlow<SyncStatusUiState> = _state.asStateFlow()

    private val _effect = Channel<SyncEffect>(Channel.BUFFERED)
    val effect: Flow<SyncEffect> = _effect.receiveAsFlow()

    private var listening = false
    private var devicesLoaded = false

    fun handleIntent(intent: SyncIntent) {
        when (intent) {
            SyncIntent.ToggleOnline -> toggleOnline()
            SyncIntent.StartListening -> startListening()
        }
    }

    private fun toggleOnline() {
        val online = !_state.value.isOnline
        _state.update { it.copy(isOnline = online) }
        if (!online) {
            viewModelScope.launch { _effect.send(SyncEffect.SyncPaused) }
        }
    }

    private fun startListening() {
        loadDevices()
        if (listening) return
        listening = true
        viewModelScope.launch {
            repository.outgoingPackets().collect { payload ->
                if (!_state.value.isOnline) return@collect
                _state.update { state ->
                    state.copy(
                        lastText = payload.text,
                        packetCount = state.packetCount + 1,
                    )
                }
            }
        }
    }

    private fun loadDevices() {
        if (devicesLoaded || deviceDirectory == null) return
        devicesLoaded = true
        viewModelScope.launch {
            _state.update { it.copy(isLoadingDevices = true) }
            val devices = deviceDirectory.devices().map { device ->
                SyncDeviceUi(
                    id = device.id,
                    name = device.name,
                    icon = device.icon,
                    isCurrent = device.isCurrent,
                )
            }
            _state.update { state ->
                state.copy(devices = devices, isLoadingDevices = false)
            }
        }
    }
}
