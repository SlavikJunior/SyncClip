package io.github.slavikjunior.syncclip.feature.history.impl.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntryType
import io.github.slavikjunior.syncclip.feature.history.impl.domain.repository.HistoryRepository
import io.github.slavikjunior.syncclip.shared.model.DeviceDirectory
import io.github.slavikjunior.syncclip.shared.model.deviceName
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class HistoryDetailViewModel(
    private val eventId: String,
    private val repository: HistoryRepository,
    private val deviceDirectory: DeviceDirectory? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryDetailUiState())
    val state: StateFlow<HistoryDetailUiState> = _state.asStateFlow()

    private val _effect = Channel<HistoryDetailEffect>(Channel.BUFFERED)
    val effect: Flow<HistoryDetailEffect> = _effect.receiveAsFlow()

    fun handleIntent(intent: HistoryDetailIntent) {
        when (intent) {
            HistoryDetailIntent.Load -> load()
            HistoryDetailIntent.Delete -> delete()
            HistoryDetailIntent.TogglePin -> togglePin()
            HistoryDetailIntent.Back -> viewModelScope.launch {
                _effect.send(HistoryDetailEffect.NavigateBack)
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            // Сбой чтения (повреждённая БД, нет места) — это «запись не
            // найдена», а не падение экрана: исключение ушло бы в
            // viewModelScope и убило приложение.
            val entry = runCatching { repository.findById(eventId) }.getOrNull()
            val deviceName = deviceDirectory?.deviceName(entry?.sourceDeviceId)
            _state.update { state ->
                state.copy(
                    entry = entry?.let {
                        HistoryEntryUi(
                            id = it.id,
                            text = it.text,
                            timestampMillis = it.timestampMillis,
                            sourceDeviceId = it.sourceDeviceId,
                            isPinned = it.isPinned,
                            sourceDeviceName = deviceName,
                            type = HistoryEntryType.fromText(it.text),
                        )
                    },
                    isLoading = false,
                )
            }
        }
    }

    private fun delete() {
        viewModelScope.launch {
            val entry = _state.value.entry ?: return@launch
            if (entry.isPinned) return@launch
            // Удаление не удалось — экран остаётся, запись видна: пользователь
            // может повторить, и приложение не падает.
            if (runCatching { repository.delete(entry.id) }.isFailure) return@launch
            _effect.send(HistoryDetailEffect.NavigateBack)
        }
    }

    private fun togglePin() {
        viewModelScope.launch {
            val entry = _state.value.entry ?: return@launch
            val pinned = !entry.isPinned
            if (runCatching { repository.setPinned(entry.id, pinned) }.isFailure) return@launch
            _state.update { it.copy(entry = it.entry?.copy(isPinned = pinned)) }
        }
    }
}
