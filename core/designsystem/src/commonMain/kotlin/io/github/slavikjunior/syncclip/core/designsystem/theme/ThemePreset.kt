package io.github.slavikjunior.syncclip.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Палитра приложения.
 *
 * [swatchColors] — цвета для превью в настройках; для [DYNAMIC] список пуст,
 * потому что цвета берутся из системной обоев через Material You.
 */
public enum class ThemePreset(public val swatchColors: List<Color>) {
    AQUA(
        listOf(
            Color(0xFFA8E1FF),
            Color(0xFF2491C9),
            Color(0xFF1A6A8C),
        ),
    ),
    WINTER(
        listOf(
            Color(0xFFA3C4D4),
            Color(0xFF8AABAF),
            Color(0xFF3C6169),
        ),
    ),
    MONOCHROME(
        listOf(
            Color(0xFFF5F5F5),
            Color(0xFF9E9E9E),
            Color(0xFF212121),
        ),
    ),
    DYNAMIC(emptyList()),
}
