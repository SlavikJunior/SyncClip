package io.github.slavikjunior.syncclip.feature.sync.impl.presentation.state

internal sealed interface SyncIntent {

    data object ToggleOnline : SyncIntent

    data object StartListening : SyncIntent
}
