package io.github.slavikjunior.syncclip.feature.history.impl.presentation.state

internal sealed interface HistoryDetailEffect {

    data object NavigateBack : HistoryDetailEffect
}
