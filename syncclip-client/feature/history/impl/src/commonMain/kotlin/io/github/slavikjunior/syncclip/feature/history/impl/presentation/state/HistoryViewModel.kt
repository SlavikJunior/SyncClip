package io.github.slavikjunior.syncclip.feature.history.impl.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.slavikjunior.syncclip.core.clipboard.ClipboardGateway
import io.github.slavikjunior.syncclip.core.clipboard.TargetedClipboardSender
import io.github.slavikjunior.syncclip.core.clipboard.TargetedSendResult
import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettings
import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettingsStore
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import syncclip.feature.history.impl.generated.resources.Res
import syncclip.feature.history.impl.generated.resources.history_copied
import syncclip.feature.history.impl.generated.resources.history_copy_failed
import syncclip.feature.history.impl.generated.resources.history_send_empty
import syncclip.feature.history.impl.generated.resources.history_send_no_devices
import syncclip.feature.history.impl.generated.resources.history_send_offline
import syncclip.feature.history.impl.generated.resources.history_sent

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class HistoryViewModel(
    private val repository: HistoryRepository,
    private val deviceDirectory: DeviceDirectory? = null,
    private val settings: ClipboardSettingsStore? = null,
    private val gateway: ClipboardGateway? = null,
    private val sender: TargetedClipboardSender? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState(isLoading = true))
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private val _effect = Channel<HistoryEffect>(Channel.BUFFERED)
    val effect: Flow<HistoryEffect> = _effect.receiveAsFlow()

    private val _query = MutableStateFlow("")
    private val _filter = MutableStateFlow(HistoryFilter.ALL)

    /**
     * Сколько строк БД уже прочитано. Пагинация идёт по строкам, а не по
     * видимым записям: фильтр по типу скрывает часть строк, и offset по
     * `entries.size` возвращал бы те же страницы снова и снова.
     */
    private var consumedRows: Int = 0
    private var hasMoreRows: Boolean = true

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
                        // Сбой чтения (повреждённая БД, нет места) не должен
                        // ни ронять коллектор, ни оставлять скелет на экране.
                        .catch { emit(emptyList()) }
                        .map { page -> Triple(query, filter, page) }
                }
                .collect { (query, filter, page) ->
                    val devices = loadDevices()
                    val entries = page
                        .map { entry -> entry.toUi(devices[entry.sourceDeviceId]) }
                        .filter { entry -> filter.matches(entry) }
                    // Запрос выполняется до update: `update` перезапускает лямбду
                    // при конкурентной записи, и suspend-вызов внутри неё
                    // выполнялся бы несколько раз. Сбой подсчёта не критичен:
                    // показываем 0 вместо падения экрана.
                    val totalCount = runCatching { repository.count(query) }.getOrDefault(0)
                    // Первая страница всегда читается с offset = 0: следующая
                    // начинается ровно за ней.
                    consumedRows = page.size
                    hasMoreRows = page.size == PAGE_SIZE
                    val more = hasMoreRows && consumedRows < totalCount
                    _state.update { current ->
                        current.copy(
                            query = query,
                            filter = filter,
                            entries = entries,
                            totalCount = totalCount,
                            hasMore = more,
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
            is HistoryIntent.ToggleFavorite -> onToggleFavorite(intent.id)
            is HistoryIntent.CopyToClipboard -> onCopy(intent.id)
            is HistoryIntent.Delete -> onDelete(intent.id)
            is HistoryIntent.Restore -> onRestore(intent.entry)
            HistoryIntent.DismissError -> _state.update { it.copy(error = null) }
            HistoryIntent.OpenSendTargets -> openSendTargets()
            HistoryIntent.DismissSendTargets -> _state.update { it.copy(sendDialog = null) }
            is HistoryIntent.ToggleSendTarget -> toggleSendTarget(intent.deviceId)
            HistoryIntent.ConfirmSend -> confirmSend()
        }
    }

    private fun onLoad() {
        viewModelScope.launch {
            val nowMillis = Clock.System.now().toEpochMilliseconds()
            // US-021: просроченные чувствительные записи удаляются при открытии
            // экрана — отдельного планировщика нет, а данные не копятся.
            runCatching { repository.clearExpiredSensitive(nowMillis) }
            // FR-4.3: лимиты берутся из настроек; без них — значения паспорта.
            val limits = settings?.current() ?: ClipboardSettings()
            val cutoff = nowMillis - limits.ttlHours * 60L * 60L * 1000L
            // Вытеснение — обслуживание истории: его сбой не должен ронять экран.
            runCatching { repository.evictExpired(cutoff, limits.maxEntries) }
        }
    }

    private fun onQueryChanged(value: String) {
        consumedRows = 0
        hasMoreRows = true
        _state.update {
            it.copy(query = value, isLoading = true, entries = emptyList(), hasMore = false)
        }
        _query.update { value }
    }

    private fun onFilterChanged(filter: HistoryFilter) {
        consumedRows = 0
        hasMoreRows = true
        _state.update { it.copy(filter = filter, isLoading = true, hasMore = false) }
        _filter.update { filter }
    }

    private fun onLoadNextPage() {
        viewModelScope.launch {
            val current = _state.value
            // Конец списка определяется прочитанными строками, а не числом
            // видимых записей: фильтр по типу прячет часть страницы.
            if (current.isLoadingMore || !hasMoreRows) return@launch
            if (consumedRows >= current.totalCount) return@launch
            _state.update { it.copy(isLoadingMore = true) }
            val offset = consumedRows
            // Ошибка чтения не должна ни ронять экран, ни оставлять его в
            // состоянии «грузится»: иначе кнопка «загрузить ещё» мертва до
            // перезапуска, а исключение уходит в viewModelScope.
            val next = runCatching {
                repository.observePage(current.query, PAGE_SIZE, offset).first()
            }.getOrNull()
            if (next == null) {
                _state.update { it.copy(isLoadingMore = false) }
                return@launch
            }
            consumedRows = offset + next.size
            hasMoreRows = next.size == PAGE_SIZE
            val more = hasMoreRows && consumedRows < current.totalCount
            val devices = loadDevices()
            val entries = next
                .map { entry -> entry.toUi(devices[entry.sourceDeviceId]) }
                .filter { entry -> current.filter.matches(entry) }
            _state.update { state ->
                state.copy(
                    entries = (state.entries + entries).distinctBy { it.id },
                    isLoadingMore = false,
                    hasMore = more,
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
            // Сбой записи — состояние не меняем: список остаётся правдой БД.
            if (runCatching { repository.setPinned(id, pinned) }.isFailure) return@launch
            _state.update { state ->
                val updated = state.entries.map {
                    if (it.id == id) it.copy(isPinned = pinned) else it
                }
                state.copy(entries = updated.filter { state.filter.matches(it) })
            }
        }
    }

    private fun onToggleFavorite(id: String) {
        viewModelScope.launch {
            val entry = _state.value.entries.firstOrNull { it.id == id } ?: return@launch
            val favorite = !entry.isFavorite
            if (runCatching { repository.setFavorite(id, favorite) }.isFailure) return@launch
            _state.update { state ->
                val updated = state.entries.map {
                    if (it.id == id) it.copy(isFavorite = favorite) else it
                }
                state.copy(entries = updated.filter { state.filter.matches(it) })
            }
        }
    }

    private fun onCopy(id: String) {
        viewModelScope.launch {
            val entry = _state.value.entries.firstOrNull { it.id == id } ?: return@launch
            val target = gateway ?: return@launch
            val copied = runCatching { target.insert(entry.text) }.isSuccess
            _effect.send(
                HistoryEffect.ShowMessage(
                    if (copied) Res.string.history_copied else Res.string.history_copy_failed,
                ),
            )
        }
    }

    private fun openSendTargets() {
        viewModelScope.launch {
            _state.update { it.copy(sendDialog = SendDialogUiState(isLoading = true)) }
            val devices = runCatching { deviceDirectory?.devices() }.getOrNull().orEmpty()
            // Себе отправлять нечего: пакет вернётся эхом и будет отброшен.
            val targets = devices
                .filterNot { device -> device.isCurrent }
                .map { device -> SendTargetUi(device.id, device.name, device.icon) }
            if (targets.isEmpty()) {
                _state.update { it.copy(sendDialog = null) }
                _effect.send(HistoryEffect.ShowMessage(Res.string.history_send_no_devices))
                return@launch
            }
            _state.update {
                it.copy(
                    sendDialog = SendDialogUiState(
                        targets = targets,
                        // По умолчанию выбраны все устройства (US-016).
                        selectedIds = targets.map { target -> target.id }.toSet(),
                    ),
                )
            }
        }
    }

    private fun toggleSendTarget(deviceId: String) {
        _state.update { current ->
            val dialog = current.sendDialog ?: return@update current
            if (dialog.targets.none { target -> target.id == deviceId }) return@update current
            val selected = if (deviceId in dialog.selectedIds) {
                dialog.selectedIds - deviceId
            } else {
                dialog.selectedIds + deviceId
            }
            current.copy(sendDialog = dialog.copy(selectedIds = selected))
        }
    }

    private fun confirmSend() {
        viewModelScope.launch {
            val dialog = _state.value.sendDialog ?: return@launch
            if (!dialog.canSend) return@launch
            _state.update { it.copy(sendDialog = dialog.copy(isSending = true)) }
            val text = runCatching { gateway?.currentText() }.getOrNull()
            if (text.isNullOrBlank()) {
                closeSendDialog(Res.string.history_send_empty)
                return@launch
            }
            val result = sender?.send(text, dialog.selectedIds.toList())
                ?: TargetedSendResult.Offline
            when (result) {
                TargetedSendResult.Queued -> closeSendDialog(Res.string.history_sent)
                TargetedSendResult.Offline -> closeSendDialog(Res.string.history_send_offline)
                TargetedSendResult.Empty -> closeSendDialog(Res.string.history_send_empty)
            }
        }
    }

    private suspend fun closeSendDialog(message: org.jetbrains.compose.resources.StringResource) {
        _state.update { it.copy(sendDialog = null) }
        _effect.send(HistoryEffect.ShowMessage(message))
    }

    private fun onDelete(id: String) {
        viewModelScope.launch {
            val entry = _state.value.entries.firstOrNull { it.id == id } ?: return@launch
            if (entry.isPinned) return@launch
            if (runCatching { repository.delete(id) }.isFailure) return@launch
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
            if (runCatching { repository.restore(entry.toDomain()) }.isFailure) return@launch
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
        isFavorite = isFavorite,
        expiresAtMillis = expiresAtMillis,
    )

    private fun HistoryEntryUi.toDomain(): HistoryEntry = HistoryEntry(
        id = id,
        text = text,
        timestampMillis = timestampMillis,
        sourceDeviceId = sourceDeviceId,
        isPinned = isPinned,
        isFavorite = isFavorite,
        expiresAtMillis = expiresAtMillis,
    )

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val PAGE_SIZE = 50
    }
}
