package io.github.slavikjunior.syncclip.feature.sync.impl

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.slavikjunior.syncclip.feature.sync.api.navigation.SyncStatusRoute
import io.github.slavikjunior.syncclip.feature.sync.impl.presentation.ui.SyncStatusScreen

/** Единственный публичный вход в фичу (INV-27). */
public fun EntryProviderScope<NavKey>.syncNavEntry() {
    entry<SyncStatusRoute> {
        SyncStatusScreen()
    }
}
