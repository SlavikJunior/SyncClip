package io.github.slavikjunior.syncclip.feature.settings.impl

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.slavikjunior.syncclip.core.designsystem.theme.FontScale
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeMode
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemePreset
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeState
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceItem
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceKind
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state.DeviceManagementUiState
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.ui.SettingsContent
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.BeforeClass
import org.junit.Test
import java.util.Locale

class SettingsContentScreenshotTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun forceRussianLocale() {
            Locale.setDefault(Locale.forLanguageTag("ru"))
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun settingsLight() {
        runDesktopComposeUiTest(width = 390, height = 844) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    SettingsContent(
                        state = fakeState(),
                        theme = ThemeState(mode = ThemeMode.LIGHT),
                        fontScale = FontScale.MEDIUM,
                        onThemeChange = {},
                        onRegenerateRandomTheme = {},
                        onFontScaleChange = {},
                        onIntent = {},
                    )
                }
            }
            onRoot().captureRoboImage("settings-light.png")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun settingsDark() {
        val darkWinter = ThemeState(mode = ThemeMode.DARK, preset = ThemePreset.WINTER)
        runDesktopComposeUiTest(width = 390, height = 844) {
            setContent {
                SyncClipTheme(theme = darkWinter) {
                    SettingsContent(
                        state = fakeState(),
                        theme = darkWinter,
                        fontScale = FontScale.MEDIUM,
                        onThemeChange = {},
                        onRegenerateRandomTheme = {},
                        onFontScaleChange = {},
                        onIntent = {},
                    )
                }
            }
            onRoot().captureRoboImage("settings-dark.png")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun settingsMonochrome() {
        val mono = ThemeState(mode = ThemeMode.LIGHT, preset = ThemePreset.MONOCHROME)
        runDesktopComposeUiTest(width = 390, height = 844) {
            setContent {
                SyncClipTheme(theme = mono) {
                    SettingsContent(
                        state = fakeState(),
                        theme = mono,
                        fontScale = FontScale.MEDIUM,
                        onThemeChange = {},
                        onRegenerateRandomTheme = {},
                        onFontScaleChange = {},
                        onIntent = {},
                    )
                }
            }
            onRoot().captureRoboImage("settings-monochrome.png")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun settingsDynamicFallback() {
        val dynamic = ThemeState(mode = ThemeMode.LIGHT, preset = ThemePreset.DYNAMIC)
        runDesktopComposeUiTest(width = 390, height = 844) {
            setContent {
                SyncClipTheme(theme = dynamic) {
                    SettingsContent(
                        state = fakeState(),
                        theme = dynamic,
                        fontScale = FontScale.MEDIUM,
                        onThemeChange = {},
                        onRegenerateRandomTheme = {},
                        onFontScaleChange = {},
                        onIntent = {},
                    )
                }
            }
            onRoot().captureRoboImage("settings-dynamic-fallback.png")
        }
    }

    /** MEDIUM-диапазон: один столбец секций, ширина ещё не ограничена. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun settingsMedium() {
        runDesktopComposeUiTest(width = 673, height = 841) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    SettingsContent(
                        state = fakeState(),
                        theme = ThemeState(mode = ThemeMode.LIGHT),
                        fontScale = FontScale.MEDIUM,
                        onThemeChange = {},
                        onRegenerateRandomTheme = {},
                        onFontScaleChange = {},
                        onIntent = {},
                    )
                }
            }
            onRoot().captureRoboImage("settings-medium.png")
        }
    }

    /** EXPANDED: контент ограничен 840 dp и центрирован, а не размазан по ширине. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun settingsWide() {
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    SettingsContent(
                        state = fakeState(),
                        theme = ThemeState(mode = ThemeMode.LIGHT),
                        fontScale = FontScale.MEDIUM,
                        onThemeChange = {},
                        onRegenerateRandomTheme = {},
                        onFontScaleChange = {},
                        onIntent = {},
                    )
                }
            }
            onRoot().captureRoboImage("settings-wide.png")
        }
    }

    private fun fakeState(): DeviceManagementUiState = DeviceManagementUiState(
        devices = listOf(
            DeviceItem(
                id = "1",
                name = "MacBook Pro",
                icon = DeviceKind.LAPTOP,
                platform = "MACOS",
                isCurrent = true,
            ),
            DeviceItem(
                id = "2",
                name = "iPhone 15 Pro",
                icon = DeviceKind.PHONE,
                platform = "IOS",
                isCurrent = false,
            ),
            DeviceItem(
                id = "3",
                name = "Pixel 9",
                icon = DeviceKind.PHONE,
                platform = "ANDROID",
                isCurrent = false,
            ),
        ),
    )
}
