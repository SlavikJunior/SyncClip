package io.github.slavikjunior.syncclip.feature.history.impl.presentation.state

internal data class HistoryDetailUiState(
    val entry: HistoryEntryUi? = null,
    val isLoading: Boolean = true,
)
