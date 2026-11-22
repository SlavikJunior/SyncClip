package io.github.slavikjunior.syncclip.feature.history.impl.presentation.state

internal sealed interface HistoryDetailIntent {

    data object Load : HistoryDetailIntent

    data object Delete : HistoryDetailIntent

    data object TogglePin : HistoryDetailIntent

    data object Back : HistoryDetailIntent
}
