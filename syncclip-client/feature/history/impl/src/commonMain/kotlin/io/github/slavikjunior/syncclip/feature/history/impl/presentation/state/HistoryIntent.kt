package io.github.slavikjunior.syncclip.feature.history.impl.presentation.state

internal sealed interface HistoryIntent {

    data object Load : HistoryIntent

    data class QueryChanged(val query: String) : HistoryIntent

    data class SetFilter(val filter: HistoryFilter) : HistoryIntent

    data object ClearQuery : HistoryIntent

    data object LoadNextPage : HistoryIntent

    data class Select(val id: String, val navigate: Boolean = true) : HistoryIntent

    data class TogglePin(val id: String) : HistoryIntent

    data class Delete(val id: String) : HistoryIntent

    data class Restore(val entry: HistoryEntryUi) : HistoryIntent

    data object DismissError : HistoryIntent
}
