package io.github.slavikjunior.syncclip.feature.auth.impl

import androidx.compose.runtime.Composable
import io.github.slavikjunior.syncclip.feature.auth.impl.presentation.ui.AuthScreen

/** Точка входа фичи для композиционного корня приложения (INV-27). */
public fun authNavEntry(
    onAuthenticated: () -> Unit,
): @Composable () -> Unit = {
    AuthScreen(onAuthenticated = onAuthenticated)
}
