package io.github.slavikjunior.syncclip.core.designsystem.theme

/** Выбранный режим, палитра темы и seed случайной палитры (US-015). */
public data class ThemeState(
    public val mode: ThemeMode = ThemeMode.SYSTEM,
    public val preset: ThemePreset = ThemePreset.AQUA,
    public val randomSeed: Long? = null,
)
