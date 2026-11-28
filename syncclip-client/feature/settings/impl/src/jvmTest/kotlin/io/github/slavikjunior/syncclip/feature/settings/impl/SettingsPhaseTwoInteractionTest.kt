package io.github.slavikjunior.syncclip.feature.settings.impl

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.slavikjunior.syncclip.core.designsystem.theme.FontScale
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeState
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state.DeviceIntent
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state.DeviceManagementUiState
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.ui.SettingsContent
import org.junit.BeforeClass
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * UI-тесты новых секций Phase 2: нажатия доходят до ViewModel, диалог экспорта
 * открывается по состоянию, а разделы не теряются из вёрстки.
 */
class SettingsPhaseTwoInteractionTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun forceEnglishLocale() {
            Locale.setDefault(Locale.forLanguageTag("en"))
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `export button asks the view model to open the format dialog`() {
        val intents = mutableListOf<DeviceIntent>()
        runDesktopComposeUiTest(width = 420, height = 1600) {
            setContent {
                SyncClipTheme {
                    SettingsContent(
                        state = DeviceManagementUiState(documentsSupported = true),
                        theme = ThemeState(),
                        fontScale = FontScale.MEDIUM,
                        onThemeChange = {},
                        onRegenerateRandomTheme = {},
                        onFontScaleChange = {},
                        onIntent = { intent -> intents += intent },
                    )
                }
            }

            onNodeWithText("Export history").performScrollTo().assertHasClickAction().performClick()
        }

        assertEquals<List<DeviceIntent>>(listOf(DeviceIntent.OpenExportDialog), intents)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `format dialog appears when state says so`() {
        runDesktopComposeUiTest(width = 420, height = 1600) {
            setContent {
                SyncClipTheme {
                    SettingsContent(
                        state = DeviceManagementUiState(
                            documentsSupported = true,
                            exportDialogVisible = true,
                        ),
                        theme = ThemeState(),
                        fontScale = FontScale.MEDIUM,
                        onThemeChange = {},
                        onRegenerateRandomTheme = {},
                        onFontScaleChange = {},
                        onIntent = {},
                    )
                }
            }

            onNodeWithText("JSON").assertIsDisplayed()
            onNodeWithText("CSV").assertIsDisplayed()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `sections of phase two are rendered`() {
        val expected = listOf("Notifications", "Privacy", "Security", "History archive")
        runDesktopComposeUiTest(width = 420, height = 2000) {
            setContent {
                SyncClipTheme {
                    SettingsContent(
                        state = DeviceManagementUiState(documentsSupported = true),
                        theme = ThemeState(),
                        fontScale = FontScale.MEDIUM,
                        onThemeChange = {},
                        onRegenerateRandomTheme = {},
                        onFontScaleChange = {},
                        onIntent = {},
                    )
                }
            }

            expected.forEach { title ->
                onNodeWithText(title).assertIsDisplayed()
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `font size options are available`() {
        runDesktopComposeUiTest(width = 420, height = 2000) {
            setContent {
                SyncClipTheme {
                    SettingsContent(
                        state = DeviceManagementUiState(),
                        theme = ThemeState(),
                        fontScale = FontScale.LARGE,
                        onThemeChange = {},
                        onRegenerateRandomTheme = {},
                        onFontScaleChange = {},
                        onIntent = {},
                    )
                }
            }

            listOf("Small", "Medium", "Large", "Extra large").forEach { label ->
                onNodeWithText(label).assertIsDisplayed()
            }
        }
    }
}
