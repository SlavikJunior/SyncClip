package io.github.slavikjunior.syncclip.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Тема приложения для всех платформ.
 *
 * Палитры: Aqua (Turbo Aqua + Aqua dark), Winter (Dark Blue Winter),
 * Monochrome, Dynamic (Material You на Android 12+, иначе — Aqua) и Random
 * (генерируется из seed, US-015). Экраны читают цвета, типографику и формы
 * только через [MaterialTheme] (INV-17).
 *
 * [fontScale] — множитель типографики из настроек (US-011): 0.85…1.3.
 */
@Composable
public fun SyncClipTheme(
    theme: ThemeState = ThemeState(),
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (theme.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val randomSeed = theme.randomSeed ?: DEFAULT_RANDOM_SEED
    val dynamicScheme = if (theme.preset == ThemePreset.DYNAMIC) {
        PlatformDynamicColorScheme(darkTheme)
    } else {
        null
    }
    val colorScheme = dynamicScheme ?: when (theme.preset) {
        ThemePreset.AQUA -> if (darkTheme) AquaDarkColorScheme else LightColorScheme
        ThemePreset.WINTER -> if (darkTheme) DarkColorScheme else WinterLightColorScheme
        ThemePreset.MONOCHROME -> if (darkTheme) MonoDarkColorScheme else MonoLightColorScheme
        ThemePreset.DYNAMIC -> if (darkTheme) DarkColorScheme else LightColorScheme
        ThemePreset.RANDOM -> RandomTheme.colorScheme(randomSeed, darkTheme)
    }
    val extendedColors = when (theme.preset) {
        ThemePreset.AQUA,
        ThemePreset.DYNAMIC,
        -> if (darkTheme) AquaDarkExtendedColors else AquaLightExtendedColors

        ThemePreset.WINTER -> if (darkTheme) AquaDarkExtendedColors else WinterLightExtendedColors
        ThemePreset.MONOCHROME -> if (darkTheme) MonoDarkExtendedColors else MonoLightExtendedColors
        ThemePreset.RANDOM -> RandomTheme.extendedColors(randomSeed, darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SyncClipTypography(fontScale),
        shapes = SyncClipShapes,
        content = {
            CompositionLocalProvider(
                LocalSyncClipColors provides extendedColors,
                content = content,
            )
        },
    )
}

/** Палитра без seed — та же, что показывается до первого выбора «Random». */
private const val DEFAULT_RANDOM_SEED = 0x2F7FD5L
