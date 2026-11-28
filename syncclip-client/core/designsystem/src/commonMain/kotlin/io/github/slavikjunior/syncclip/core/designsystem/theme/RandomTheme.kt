package io.github.slavikjunior.syncclip.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

/**
 * Генерация палитры из seed-цвета (US-015).
 *
 * Схема строится в HSL: один тон задаёт primary, вторичные тона сдвинуты на
 * 40° и 80°, нейтрали тонированы тем же тоном с низкой насыщенностью. Контраст
 * «текст на фоне» держится за счёт фиксированной светлоты, а не только hue,
 * поэтому случайная тема не делает интерфейс нечитаемым.
 */
public object RandomTheme {

    /** Новый seed для «Random»: 24 бита тона, остальное — вариация насыщенности. */
    public fun newSeed(random: Random = Random.Default): Long =
        random.nextLong(0x100000000L, 0x7FFFFFFF_FFFFL)

    public fun hueOf(seed: Long): Float {
        val normalized = (seed and HUE_MASK).toDouble() / HUE_MASK.toDouble()
        return (normalized * 360.0).toFloat()
    }

    public fun colorScheme(seed: Long, dark: Boolean): ColorScheme {
        val hue = hueOf(seed)
        val secondaryHue = (hue + 40f) % 360f
        val tertiaryHue = (hue + 80f) % 360f
        return if (dark) darkScheme(hue, secondaryHue, tertiaryHue) else lightScheme(
            hue,
            secondaryHue,
            tertiaryHue,
        )
    }

    public fun extendedColors(seed: Long, dark: Boolean): SyncClipExtendedColors {
        val hue = hueOf(seed)
        val success = if (dark) hsl(150f, 0.45f, 0.70f) else hsl(150f, 0.55f, 0.34f)
        return SyncClipExtendedColors(
            success = success,
            warning = if (dark) hsl(38f, 0.85f, 0.60f) else hsl(38f, 0.90f, 0.42f),
            info = if (dark) hsl(hue, 0.55f, 0.72f) else hsl(hue, 0.55f, 0.40f),
            online = success,
            offline = if (dark) hsl(hue, 0.08f, 0.60f) else hsl(hue, 0.08f, 0.50f),
            syncStart = if (dark) hsl(hue, 0.50f, 0.68f) else hsl(hue, 0.55f, 0.42f),
            syncEnd = if (dark) hsl((hue + 30f) % 360f, 0.35f, 0.78f) else {
                hsl((hue + 30f) % 360f, 0.45f, 0.62f)
            },
        )
    }

    /** Три цвета для кружка-превью в настройках. */
    public fun swatch(seed: Long): List<Color> {
        val hue = hueOf(seed)
        return listOf(
            hsl(hue, 0.55f, 0.78f),
            hsl(hue, 0.60f, 0.48f),
            hsl((hue + 40f) % 360f, 0.45f, 0.30f),
        )
    }

    private fun lightScheme(hue: Float, secondaryHue: Float, tertiaryHue: Float): ColorScheme =
        lightColorScheme(
            primary = hsl(hue, 0.58f, 0.38f),
            onPrimary = WHITE,
            primaryContainer = hsl(hue, 0.60f, 0.90f),
            onPrimaryContainer = hsl(hue, 0.60f, 0.16f),
            inversePrimary = hsl(hue, 0.50f, 0.78f),
            secondary = hsl(secondaryHue, 0.35f, 0.42f),
            onSecondary = WHITE,
            secondaryContainer = hsl(secondaryHue, 0.40f, 0.90f),
            onSecondaryContainer = hsl(secondaryHue, 0.40f, 0.16f),
            tertiary = hsl(tertiaryHue, 0.35f, 0.40f),
            onTertiary = WHITE,
            tertiaryContainer = hsl(tertiaryHue, 0.40f, 0.90f),
            onTertiaryContainer = hsl(tertiaryHue, 0.40f, 0.16f),
            background = hsl(hue, 0.22f, 0.97f),
            onBackground = hsl(hue, 0.25f, 0.10f),
            surface = hsl(hue, 0.25f, 0.99f),
            onSurface = hsl(hue, 0.25f, 0.10f),
            surfaceVariant = hsl(hue, 0.22f, 0.91f),
            onSurfaceVariant = hsl(hue, 0.12f, 0.38f),
            surfaceTint = hsl(hue, 0.58f, 0.38f),
            inverseSurface = hsl(hue, 0.12f, 0.20f),
            inverseOnSurface = hsl(hue, 0.12f, 0.94f),
            error = ERROR_LIGHT,
            onError = WHITE,
            errorContainer = ERROR_CONTAINER_LIGHT,
            onErrorContainer = ERROR_ON_CONTAINER_LIGHT,
            outline = hsl(hue, 0.08f, 0.48f),
            outlineVariant = hsl(hue, 0.14f, 0.84f),
            scrim = BLACK,
            surfaceBright = hsl(hue, 0.25f, 1.0f),
            surfaceDim = hsl(hue, 0.18f, 0.90f),
            surfaceContainerLowest = WHITE,
            surfaceContainerLow = hsl(hue, 0.25f, 0.98f),
            surfaceContainer = hsl(hue, 0.24f, 0.96f),
            surfaceContainerHigh = hsl(hue, 0.22f, 0.93f),
            surfaceContainerHighest = hsl(hue, 0.20f, 0.90f),
        )

    private fun darkScheme(hue: Float, secondaryHue: Float, tertiaryHue: Float): ColorScheme =
        darkColorScheme(
            primary = hsl(hue, 0.55f, 0.76f),
            onPrimary = hsl(hue, 0.60f, 0.14f),
            primaryContainer = hsl(hue, 0.45f, 0.26f),
            onPrimaryContainer = hsl(hue, 0.55f, 0.90f),
            inversePrimary = hsl(hue, 0.55f, 0.42f),
            secondary = hsl(secondaryHue, 0.32f, 0.72f),
            onSecondary = hsl(secondaryHue, 0.35f, 0.14f),
            secondaryContainer = hsl(secondaryHue, 0.30f, 0.24f),
            onSecondaryContainer = hsl(secondaryHue, 0.35f, 0.88f),
            tertiary = hsl(tertiaryHue, 0.32f, 0.72f),
            onTertiary = hsl(tertiaryHue, 0.35f, 0.14f),
            tertiaryContainer = hsl(tertiaryHue, 0.30f, 0.24f),
            onTertiaryContainer = hsl(tertiaryHue, 0.35f, 0.88f),
            background = hsl(hue, 0.28f, 0.07f),
            onBackground = hsl(hue, 0.15f, 0.92f),
            surface = hsl(hue, 0.25f, 0.09f),
            onSurface = hsl(hue, 0.15f, 0.92f),
            surfaceVariant = hsl(hue, 0.18f, 0.20f),
            onSurfaceVariant = hsl(hue, 0.12f, 0.78f),
            surfaceTint = hsl(hue, 0.55f, 0.76f),
            inverseSurface = hsl(hue, 0.15f, 0.92f),
            inverseOnSurface = hsl(hue, 0.25f, 0.12f),
            error = hsl(0f, 0.70f, 0.72f),
            onError = hsl(0f, 0.80f, 0.16f),
            errorContainer = hsl(0f, 0.60f, 0.28f),
            onErrorContainer = hsl(0f, 0.70f, 0.90f),
            outline = hsl(hue, 0.10f, 0.58f),
            outlineVariant = hsl(hue, 0.14f, 0.26f),
            scrim = BLACK,
            surfaceBright = hsl(hue, 0.18f, 0.22f),
            surfaceDim = hsl(hue, 0.25f, 0.09f),
            surfaceContainerLowest = hsl(hue, 0.30f, 0.05f),
            surfaceContainerLow = hsl(hue, 0.26f, 0.11f),
            surfaceContainer = hsl(hue, 0.24f, 0.13f),
            surfaceContainerHigh = hsl(hue, 0.22f, 0.16f),
            surfaceContainerHighest = hsl(hue, 0.20f, 0.19f),
        )

    /** HSL → sRGB без платформенных зависимостей: одинаково на всех таргетах. */
    private fun hsl(hue: Float, saturation: Float, lightness: Float): Color {
        val h = ((hue % 360f) + 360f) % 360f / 360f
        val s = saturation.coerceIn(0f, 1f)
        val l = lightness.coerceIn(0f, 1f)
        if (s == 0f) return Color(l, l, l)
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        return Color(
            red = channel(p, q, h + 1f / 3f),
            green = channel(p, q, h),
            blue = channel(p, q, h - 1f / 3f),
        )
    }

    private fun channel(p: Float, q: Float, t: Float): Float {
        var value = t
        if (value < 0f) value += 1f
        if (value > 1f) value -= 1f
        return when {
            value < 1f / 6f -> p + (q - p) * 6f * value
            value < 1f / 2f -> q
            value < 2f / 3f -> p + (q - p) * (2f / 3f - value) * 6f
            else -> p
        }
    }

    private const val HUE_MASK = 0xFFFFFFL
    private val WHITE = Color(0xFFFFFFFF)
    private val BLACK = Color(0xFF000000)
    private val ERROR_LIGHT = Color(0xFFBA1A1A)
    private val ERROR_CONTAINER_LIGHT = Color(0xFFFFDAD6)
    private val ERROR_ON_CONTAINER_LIGHT = Color(0xFF410002)
}
