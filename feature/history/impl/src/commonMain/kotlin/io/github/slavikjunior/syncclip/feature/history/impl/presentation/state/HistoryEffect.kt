package io.github.slavikjunior.syncclip.feature.history.impl.presentation.state

import org.jetbrains.compose.resources.StringResource

internal sealed interface HistoryEffect {

    data class ShowUndo(val entry: HistoryEntryUi) : HistoryEffect

    data class ShowMessage(val message: StringResource) : HistoryEffect

    data class NavigateToDetail(val id: String) : HistoryEffect
}
