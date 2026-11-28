package io.github.slavikjunior.syncclip.feature.settings.impl

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.slavikjunior.syncclip.core.designsystem.theme.FontScale
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeState
import io.github.slavikjunior.syncclip.feature.settings.api.navigation.SettingsRoute
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.ui.SettingsScreen

/**
 * Единственный публичный вход в фичу для `entryProvider` приложения (INV-27).
 *
 * Тема и размер шрифта принадлежат композиции (`:app:common`) и передаются
 * сюда: экран настроек не владеет ими, а только сообщает о выборе.
 */
public fun EntryProviderScope<NavKey>.settingsNavEntry(
    theme: ThemeState,
    fontScale: FontScale,
    onThemeChange: (ThemeState) -> Unit,
    onRegenerateRandomTheme: () -> Unit,
    onFontScaleChange: (FontScale) -> Unit,
) {
    entry<SettingsRoute> {
        SettingsScreen(
            theme = theme,
            fontScale = fontScale,
            onThemeChange = onThemeChange,
            onRegenerateRandomTheme = onRegenerateRandomTheme,
            onFontScaleChange = onFontScaleChange,
        )
    }
}
