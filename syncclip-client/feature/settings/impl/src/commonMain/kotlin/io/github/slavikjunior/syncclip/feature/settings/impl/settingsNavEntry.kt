package io.github.slavikjunior.syncclip.feature.settings.impl

import androidx.compose.runtime.Composable
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeState
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.ui.SettingsScreen

/** Точка входа фичи для композиционного корня приложения (INV-27). */
public fun settingsNavEntry(
    theme: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
): @Composable () -> Unit = {
    SettingsScreen(theme = theme, onThemeChange = onThemeChange)
}
