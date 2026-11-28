package io.github.slavikjunior.syncclip.core.designsystem.theme

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RandomThemeTest {

    @Test
    fun `same seed produces the same palette`() {
        val first = RandomTheme.colorScheme(seed = 0x2F7FD5L, dark = false)
        val second = RandomTheme.colorScheme(seed = 0x2F7FD5L, dark = false)

        assertEquals(first.primary, second.primary)
        assertEquals(first.surface, second.surface)
    }

    @Test
    fun `different seeds produce different palettes`() {
        val first = RandomTheme.colorScheme(seed = 0x100000L, dark = false)
        val second = RandomTheme.colorScheme(seed = 0x700000L, dark = false)

        assertNotEquals(first.primary, second.primary)
    }

    @Test
    fun `dark scheme differs from light for the same seed`() {
        val light = RandomTheme.colorScheme(seed = 0x2F7FD5L, dark = false)
        val dark = RandomTheme.colorScheme(seed = 0x2F7FD5L, dark = true)

        assertNotEquals(light.background, dark.background)
        assertNotEquals(light.primary, dark.primary)
    }

    @Test
    fun `hue stays inside the circle for any seed`() {
        val random = Random(42)
        repeat(100) {
            val hue = RandomTheme.hueOf(random.nextLong())
            assertTrue(hue >= 0f && hue < 360f, "hue $hue вне диапазона")
        }
    }

    @Test
    fun `swatch has three distinct colors`() {
        val swatch = RandomTheme.swatch(seed = 0x2F7FD5L)

        assertEquals(3, swatch.size)
        assertEquals(3, swatch.distinct().size)
    }

    @Test
    fun `new seed generates a different palette each time`() {
        val random = Random(7)
        val first = RandomTheme.newSeed(random)
        val second = RandomTheme.newSeed(random)

        assertNotEquals(first, second)
    }

    @Test
    fun `font scale resolves to the nearest step`() {
        assertEquals(FontScale.SMALL, FontScale.fromMultiplier(0.85f))
        assertEquals(FontScale.MEDIUM, FontScale.fromMultiplier(1.0f))
        assertEquals(FontScale.LARGE, FontScale.fromMultiplier(1.15f))
        assertEquals(FontScale.EXTRA_LARGE, FontScale.fromMultiplier(1.3f))
        // Мусор из повреждённого файла не должен выбирать случайный шаг.
        assertEquals(FontScale.MEDIUM, FontScale.fromMultiplier(0.1f))
        assertEquals(FontScale.MEDIUM, FontScale.fromMultiplier(3.0f))
    }

    @Test
    fun `typography scaling multiplies sizes`() {
        val base = androidx.compose.material3.Typography()
        val scaled = base.scaled(1.3f)

        assertEquals(base.bodyLarge.fontSize * 1.3f, scaled.bodyLarge.fontSize)
        assertEquals(base.headlineMedium.lineHeight * 1.3f, scaled.headlineMedium.lineHeight)
    }
}
