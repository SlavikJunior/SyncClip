package io.github.slavikjunior.syncclip.core.datastore

/**
 * Допустимые значения настроек Phase 2.
 *
 * Проверка нужна на двух сторонах: UI не даёт сохранить бессмыслицу, а чтение
 * из повреждённого DataStore подменяет невалидное значение на default — иначе
 * множитель шрифта `0` из битого файла сделал бы весь текст невидимым.
 */
public object AppSettingsRules {

    public val FONT_SCALES: List<Float> = listOf(0.85f, 1.0f, 1.15f, 1.3f)

    public const val MIN_TIMEOUT_MINUTES: Int = 0
    public const val MAX_TIMEOUT_MINUTES: Int = 15

    public val THEME_MODES: Set<String> = setOf("SYSTEM", "LIGHT", "DARK")
    public val THEME_PRESETS: Set<String> = setOf("AQUA", "WINTER", "MONOCHROME", "DYNAMIC", "RANDOM")

    public fun isValidFontScale(value: Float): Boolean =
        FONT_SCALES.any { allowed -> kotlin.math.abs(allowed - value) < 0.001f }

    /** Есть ли такой шаг в допустимом списке (0 — «сразу при сворачивании»). */
    public fun isValidTimeout(value: Int): Boolean = value in MIN_TIMEOUT_MINUTES..MAX_TIMEOUT_MINUTES

    public fun isValidThemeMode(value: String): Boolean = value in THEME_MODES

    public fun isValidThemePreset(value: String): Boolean = value in THEME_PRESETS

    /** Множитель шрифта или default, если значение вне списка. */
    public fun fontScaleOr(value: Float?): Float = value
        ?.takeIf(::isValidFontScale)
        ?: AppearanceSettings.DEFAULT_FONT_SCALE

    public fun timeoutOr(value: Int?): Int = value
        ?.takeIf(::isValidTimeout)
        ?: SecuritySettings.DEFAULT_TIMEOUT_MINUTES

    public fun themeModeOr(value: String?): String = value
        ?.takeIf(::isValidThemeMode)
        ?: "SYSTEM"

    public fun themePresetOr(value: String?): String = value
        ?.takeIf(::isValidThemePreset)
        ?: "AQUA"
}
