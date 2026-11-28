package io.github.slavikjunior.syncclip.feature.auth.impl

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.slavikjunior.syncclip.feature.auth.api.navigation.AuthRoute
import io.github.slavikjunior.syncclip.feature.auth.impl.presentation.ui.AuthScreen

/** Единственный публичный вход в фичу (INV-27). */
public fun EntryProviderScope<NavKey>.authNavEntry(
    onAuthenticated: () -> Unit,
) {
    entry<AuthRoute> {
        AuthScreen(onAuthenticated = onAuthenticated)
    }
}
