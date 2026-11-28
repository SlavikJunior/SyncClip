package io.github.slavikjunior.syncclip.feature.sync.impl.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettingsStore
import io.github.slavikjunior.syncclip.feature.sync.api.ClipboardCaptureStarter
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncConnectionStatus
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardCaptureFeed
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

/**
 * Статус синхронизации.
 *
 * Тумблеры не дублируются в состоянии: источник правды — [ClipboardSettingsStore],
 * а [SyncStatusUiState] показывает его снимок. Иначе после перезапуска
 * приложения UI показывал бы прошлое значение.
 */
internal class SyncStatusViewModel(
    private val captures: ClipboardCaptureFeed,
    private val packets: ClipboardPacketSource,
    private val settings: ClipboardSettingsStore,
    private val starter: ClipboardCaptureStarter,
    private val deviceDirectory: DeviceDirectory? = null,
    private val connection: StateFlow<SyncConnectionStatus>? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(SyncStatusUiState())
    val state: StateFlow<SyncStatusUiState> = _state.asStateFlow()

    private val _effect = Channel<SyncEffect>(Channel.BUFFERED)
    val effect: Flow<SyncEffect> = _effect.receiveAsFlow()

    private var devicesLoaded = false

    init {
        viewModelScope.launch {
            settings.state.collect { current ->
                _state.update {
                    it.copy(isOnline = current.isOnline, autoInsert = current.autoInsert)
                }
            }
        }
        viewModelScope.launch {
            captures.captures().collect { packet ->
                _state.update { it.copy(lastText = packet.text) }
            }
        }
        viewModelScope.launch {
            packets.outgoingPackets().collect {
                _state.update { state -> state.copy(packetCount = state.packetCount + 1) }
            }
        }
        // FR-2.3: состояние соединения показывается пользователю. Подписка
        // живёт столько же, сколько экран; транспорт — `single`, поэтому
        // исключение из его потока не может уронить подписчика.
        connection?.let { status ->
            viewModelScope.launch {
                status.collect { value -> _state.update { it.copy(connectionStatus = value) } }
            }
        }
    }

    fun handleIntent(intent: SyncIntent) {
        when (intent) {
            SyncIntent.ToggleOnline -> toggleOnline()
            SyncIntent.ToggleAutoInsert -> toggleAutoInsert()
            SyncIntent.CaptureCurrent -> viewModelScope.launch { starter.captureCurrent() }
            SyncIntent.StartListening -> loadDevices()
        }
    }

    private fun toggleOnline() {
        val online = !_state.value.isOnline
        viewModelScope.launch {
            // Сбой записи DataStore (переполненный диск, повреждённый файл) не
            // должен выбрасывать исключение в viewModelScope: приложение упало
            // бы на простом тапе по тумблеру. Экран следует за store, поэтому
            // при неудаче переключатель остаётся в прежнем положении.
            val applied = runCatching { settings.setOnline(online) }.isSuccess
            if (applied && !online) _effect.send(SyncEffect.SyncPaused)
        }
    }

    private fun toggleAutoInsert() {
        val autoInsert = !_state.value.autoInsert
        viewModelScope.launch {
            // См. [toggleOnline]: ошибка записи — не повод падать.
            runCatching { settings.setAutoInsert(autoInsert) }
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
