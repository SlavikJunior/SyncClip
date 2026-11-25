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
    val error: StringResource? = null,
)
