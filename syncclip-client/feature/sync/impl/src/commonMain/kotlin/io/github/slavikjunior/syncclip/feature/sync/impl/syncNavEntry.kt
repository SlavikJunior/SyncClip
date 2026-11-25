package io.github.slavikjunior.syncclip.feature.sync.impl

import androidx.compose.runtime.Composable
import io.github.slavikjunior.syncclip.feature.sync.impl.presentation.ui.SyncStatusScreen

/** Единственный публичный вход в фичу (INV-27). */
public fun syncNavEntry(): @Composable () -> Unit = {
    SyncStatusScreen()
}
