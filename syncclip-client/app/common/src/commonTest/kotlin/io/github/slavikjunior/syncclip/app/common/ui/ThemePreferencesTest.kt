package io.github.slavikjunior.syncclip.app.common.ui

import io.github.slavikjunior.syncclip.core.datastore.AppearanceSettings
import io.github.slavikjunior.syncclip.core.designsystem.theme.FontScale
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeMode
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemePreset
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemePreferencesTest {

    @Test
    fun `stored appearance is mapped to theme state`() {
        val state = AppearanceSettings(
            themeMode = "DARK",
            themePreset = "RANDOM",
            randomThemeSeed = 42L,
            fontScale = 1.15f,
        ).toThemeState()

        assertEquals(ThemeMode.DARK, state.mode)
        assertEquals(ThemePreset.RANDOM, state.preset)
        assertEquals(42L, state.randomSeed)
        assertEquals(FontScale.LARGE, AppearanceSettings(fontScale = 1.15f).toFontScale())
    }

    @Test
    fun `unknown values from disk fall back to defaults`() {
        val state = AppearanceSettings(themeMode = "NEON", themePreset = "NEON").toThemeState()

        assertEquals(ThemeMode.SYSTEM, state.mode)
        assertEquals(ThemePreset.AQUA, state.preset)
    }
}
