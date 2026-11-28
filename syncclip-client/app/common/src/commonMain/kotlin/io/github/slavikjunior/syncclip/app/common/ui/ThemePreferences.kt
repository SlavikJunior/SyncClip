package io.github.slavikjunior.syncclip.app.common.ui

import io.github.slavikjunior.syncclip.core.datastore.AppearanceSettings
import io.github.slavikjunior.syncclip.core.designsystem.theme.FontScale
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeMode
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemePreset
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeState

/**
 * Мост между хранимыми строками настроек и enum'ами дизайн-системы.
 *
 * `:core:datastore` не знает про UI, поэтому хранит строки; неизвестное значение
 * (старая версия, ручная правка файла) трактуется как default, а не роняет
 * композицию.
 */
internal fun AppearanceSettings.toThemeState(): ThemeState = ThemeState(
    mode = ThemeMode.entries.firstOrNull { it.name == themeMode } ?: ThemeMode.SYSTEM,
    preset = ThemePreset.entries.firstOrNull { it.name == themePreset } ?: ThemePreset.AQUA,
    randomSeed = randomThemeSeed,
)

/** Множитель шрифта из настроек → ближайший шаг UI. */
internal fun AppearanceSettings.toFontScale(): FontScale = FontScale.fromMultiplier(fontScale)
