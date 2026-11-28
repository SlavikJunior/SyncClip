package io.github.slavikjunior.syncclip.feature.history.impl.presentation.state

import org.jetbrains.compose.resources.StringResource

internal data class HistoryUiState(
    val entries: List<HistoryEntryUi> = emptyList(),
    val query: String = "",
    val filter: HistoryFilter = HistoryFilter.ALL,
    val selectedEntryId: String? = null,
    val totalCount: Int = 0,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    /**
     * Есть ли ещё строки в БД за уже прочитанными.
     *
     * Нельзя выводить из `entries.size < totalCount`: фильтр по типу скрывает
     * часть строк, и кнопка «загрузить ещё» оставалась бы навсегда, хотя
     * запрашивать больше нечего.
     */
    val hasMore: Boolean = true,
    val error: StringResource? = null,
    /** US-016: открытый диалог отправки; `null` — закрыт. */
    val sendDialog: SendDialogUiState? = null,
)
