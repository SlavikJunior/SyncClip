package io.github.slavikjunior.syncclip.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Тема приложения для всех платформ.
 *
 * Палитры: Aqua (Turbo Aqua + Aqua dark), Winter (Dark Blue Winter),
 * Monochrome и Dynamic (Material You на Android 12+, иначе — Aqua).
 * Экраны читают цвета, типографику и формы только через [MaterialTheme]
 * (INV-17).
 */
@Composable
public fun SyncClipTheme(
    theme: ThemeState = ThemeState(),
    content: @Composable () -> Unit,
) {
    val darkTheme = when (theme.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
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
    }
    val extendedColors = when (theme.preset) {
        ThemePreset.AQUA,
        ThemePreset.DYNAMIC,
        -> if (darkTheme) AquaDarkExtendedColors else AquaLightExtendedColors

        ThemePreset.WINTER -> if (darkTheme) AquaDarkExtendedColors else WinterLightExtendedColors
        ThemePreset.MONOCHROME -> if (darkTheme) MonoDarkExtendedColors else MonoLightExtendedColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SyncClipTypography(),
        shapes = SyncClipShapes,
        content = {
            CompositionLocalProvider(
                LocalSyncClipColors provides extendedColors,
                content = content,
            )
        },
    )
}
