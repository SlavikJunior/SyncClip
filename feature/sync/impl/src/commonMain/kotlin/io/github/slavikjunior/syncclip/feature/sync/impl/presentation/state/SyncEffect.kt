package io.github.slavikjunior.syncclip.feature.sync.impl.presentation.state

internal sealed interface SyncEffect {

    data object SyncPaused : SyncEffect
}
