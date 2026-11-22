package io.github.slavikjunior.syncclip.core.designsystem.theme

/** Выбранный режим и палитра темы. */
public data class ThemeState(
    public val mode: ThemeMode = ThemeMode.SYSTEM,
    public val preset: ThemePreset = ThemePreset.AQUA,
)
