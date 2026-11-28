package io.github.slavikjunior.syncclip.core.datastore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppSettingsRulesTest {

    @Test
    fun `only four font steps are accepted`() {
        AppSettingsRules.FONT_SCALES.forEach { scale ->
            assertTrue(AppSettingsRules.isValidFontScale(scale), "шаг $scale должен быть допустим")
        }
        assertFalse(AppSettingsRules.isValidFontScale(0f))
        assertFalse(AppSettingsRules.isValidFontScale(2.5f))
    }

    @Test
    fun `invalid multiplier falls back to default instead of hiding text`() {
        assertEquals(
            AppearanceSettings.DEFAULT_FONT_SCALE,
            AppSettingsRules.fontScaleOr(0f),
        )
        assertEquals(1.15f, AppSettingsRules.fontScaleOr(1.15f))
    }

    @Test
    fun `biometric timeout accepts only known window`() {
        assertTrue(AppSettingsRules.isValidTimeout(0))
        assertTrue(AppSettingsRules.isValidTimeout(15))
        assertFalse(AppSettingsRules.isValidTimeout(-1))
        assertFalse(AppSettingsRules.isValidTimeout(120))
        assertEquals(
            SecuritySettings.DEFAULT_TIMEOUT_MINUTES,
            AppSettingsRules.timeoutOr(1_000),
        )
    }

    @Test
    fun `unknown theme keys are replaced by defaults`() {
        assertEquals("SYSTEM", AppSettingsRules.themeModeOr("NEON"))
        assertEquals("AQUA", AppSettingsRules.themePresetOr("neon"))
        assertEquals("RANDOM", AppSettingsRules.themePresetOr("RANDOM"))
    }
}
