package io.github.slavikjunior.syncclip.feature.settings.impl

import androidx.compose.runtime.Composable
import io.github.slavikjunior.syncclip.core.designsystem.theme.FontScale
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeState
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.ui.SettingsScreen

/**
 * Точка входа фичи для композиционного корня приложения (INV-27).
 *
 * Тема и размер шрифта принадлежат композиции (`:app:common`) и передаются
 * сюда: экран настроек не владеет ими, а только сообщает о выборе.
 */
public fun settingsNavEntry(
    theme: ThemeState,
    fontScale: FontScale,
    onThemeChange: (ThemeState) -> Unit,
    onRegenerateRandomTheme: () -> Unit,
    onFontScaleChange: (FontScale) -> Unit,
): @Composable () -> Unit = {
    SettingsScreen(
        theme = theme,
        fontScale = fontScale,
        onThemeChange = onThemeChange,
        onRegenerateRandomTheme = onRegenerateRandomTheme,
        onFontScaleChange = onFontScaleChange,
    )
}
