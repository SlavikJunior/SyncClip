package io.github.slavikjunior.syncclip.feature.history.impl.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntry
import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntryType
import io.github.slavikjunior.syncclip.feature.history.impl.domain.repository.HistoryRepository
import io.github.slavikjunior.syncclip.shared.model.Device
import io.github.slavikjunior.syncclip.shared.model.DeviceDirectory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class HistoryViewModel(
    private val repository: HistoryRepository,
    private val deviceDirectory: DeviceDirectory? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState(isLoading = true))
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private val _effect = Channel<HistoryEffect>(Channel.BUFFERED)
    val effect: Flow<HistoryEffect> = _effect.receiveAsFlow()

    private val _query = MutableStateFlow("")
    private val _filter = MutableStateFlow(HistoryFilter.ALL)

    init {
        // Сначала показываем прошлый снимок первого экрана из ОЗУ (NFR < 50 мс),
        // затем Room присылает актуальную страницу и заменяет его.
        viewModelScope.launch { showCachedPage() }

        viewModelScope.launch {
            _query
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .combine(_filter) { query, filter -> query to filter }
                .flatMapLatest { (query, filter) ->
                    repository.observePage(query, PAGE_SIZE, 0)
                        .map { page -> Triple(query, filter, page) }
                }
                .collect { (query, filter, page) ->
                    val devices = loadDevices()
                    val entries = page
                        .map { entry -> entry.toUi(devices[entry.sourceDeviceId]) }
                        .filter { entry -> filter.matches(entry) }
                    _state.update { current ->
                        current.copy(
                            query = query,
                            filter = filter,
                            entries = entries,
                            totalCount = repository.count(query),
                            isLoading = false,
                        )
                    }
                }
        }
    }

    /**
     * Заполняет экран из кэша, только если Room ещё ничего не прислал: иначе
     * устаревший снимок перезаписал бы актуальные данные и остался бы на экране
     * до следующего изменения в БД.
     */
    private suspend fun showCachedPage() {
        if (!_state.value.isLoading) return
        val cached = repository.cachedFirstPage(PAGE_SIZE) ?: return
        val devices = loadDevices()
        val entries = cached.map { entry -> entry.toUi(devices[entry.sourceDeviceId]) }
        _state.update { current ->
            if (current.isLoading && current.entries.isEmpty()) {
                current.copy(
                    entries = entries.filter { entry -> current.filter.matches(entry) },
                    // totalCount ещё неизвестен: не показываем «N из 0», пока Room не ответит.
                    totalCount = current.totalCount.coerceAtLeast(entries.size),
                )
            } else {
                current
            }
        }
    }

    fun handleIntent(intent: HistoryIntent) {
        when (intent) {
            HistoryIntent.Load -> onLoad()
            is HistoryIntent.QueryChanged -> onQueryChanged(intent.query)
            is HistoryIntent.SetFilter -> onFilterChanged(intent.filter)
            HistoryIntent.ClearQuery -> onQueryChanged("")
            HistoryIntent.LoadNextPage -> onLoadNextPage()
            is HistoryIntent.Select -> onSelect(intent.id, intent.navigate)
            is HistoryIntent.TogglePin -> onTogglePin(intent.id)
            is HistoryIntent.Delete -> onDelete(intent.id)
            is HistoryIntent.Restore -> onRestore(intent.entry)
            HistoryIntent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun onLoad() {
        viewModelScope.launch {
            val cutoff = Clock.System.now().toEpochMilliseconds() - TTL_MILLIS
            repository.evictExpired(cutoff, MAX_LOCAL_ENTRIES)
        }
    }

    private fun onQueryChanged(value: String) {
        _state.update { it.copy(query = value, isLoading = true, entries = emptyList()) }
        _query.update { value }
    }

    private fun onFilterChanged(filter: HistoryFilter) {
        _state.update { it.copy(filter = filter, isLoading = true) }
        _filter.update { filter }
    }

    private fun onLoadNextPage() {
        viewModelScope.launch {
            val current = _state.value
            if (current.isLoadingMore || current.entries.size >= current.totalCount) return@launch
            _state.update { it.copy(isLoadingMore = true) }
            val next = repository
                .observePage(current.query, PAGE_SIZE, current.entries.size)
                .first()
            val devices = loadDevices()
            val entries = next
                .map { entry -> entry.toUi(devices[entry.sourceDeviceId]) }
                .filter { entry -> current.filter.matches(entry) }
            _state.update { state ->
                state.copy(
                    entries = (state.entries + entries).distinctBy { it.id },
                    isLoadingMore = false,
                )
            }
        }
    }

    private fun onSelect(id: String, navigate: Boolean) {
        _state.update { it.copy(selectedEntryId = id) }
        if (navigate) {
            viewModelScope.launch { _effect.send(HistoryEffect.NavigateToDetail(id)) }
        }
    }

    private fun onTogglePin(id: String) {
        viewModelScope.launch {
            val entry = _state.value.entries.firstOrNull { it.id == id } ?: return@launch
            val pinned = !entry.isPinned
            repository.setPinned(id, pinned)
            _state.update { state ->
                val updated = state.entries.map {
                    if (it.id == id) it.copy(isPinned = pinned) else it
                }
                state.copy(entries = updated.filter { state.filter.matches(it) })
            }
        }
    }

    private fun onDelete(id: String) {
        viewModelScope.launch {
            val entry = _state.value.entries.firstOrNull { it.id == id } ?: return@launch
            if (entry.isPinned) return@launch
            repository.delete(id)
            _state.update { state ->
                state.copy(
                    entries = state.entries.filterNot { it.id == id },
                    selectedEntryId = if (state.selectedEntryId == id) null else state.selectedEntryId,
                )
            }
            _effect.send(HistoryEffect.ShowUndo(entry))
        }
    }

    private fun onRestore(entry: HistoryEntryUi) {
        viewModelScope.launch {
            repository.restore(entry.toDomain())
            _state.update { state ->
                state.copy(
                    entries = (listOf(entry) + state.entries)
                        .distinctBy { it.id }
                        .filter { state.filter.matches(it) },
                )
            }
        }
    }

    private suspend fun loadDevices(): Map<String, Device> {
        val directory = deviceDirectory ?: return emptyMap()
        return directory.devices().associateBy { device -> device.id }
    }

    private fun HistoryEntry.toUi(device: Device?): HistoryEntryUi = HistoryEntryUi(
        id = id,
        text = text,
        timestampMillis = timestampMillis,
        sourceDeviceId = sourceDeviceId,
        isPinned = isPinned,
        sourceDeviceName = device?.name,
        sourceIcon = device?.icon,
        type = HistoryEntryType.fromText(text),
    )

    private fun HistoryEntryUi.toDomain(): HistoryEntry = HistoryEntry(
        id = id,
        text = text,
        timestampMillis = timestampMillis,
        sourceDeviceId = sourceDeviceId,
        isPinned = isPinned,
    )

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val PAGE_SIZE = 50
        const val MAX_LOCAL_ENTRIES = 200
        const val TTL_MILLIS = 7L * 24L * 60L * 60L * 1_000L
    }
}
