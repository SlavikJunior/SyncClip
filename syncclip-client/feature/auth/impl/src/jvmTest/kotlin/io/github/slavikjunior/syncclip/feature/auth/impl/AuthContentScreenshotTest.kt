package io.github.slavikjunior.syncclip.feature.auth.impl

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeMode
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeState
import io.github.slavikjunior.syncclip.feature.auth.impl.presentation.state.AuthUiState
import io.github.slavikjunior.syncclip.feature.auth.impl.presentation.ui.AuthContent
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.BeforeClass
import org.junit.Test
import java.util.Locale

/**
 * Baseline формы входа на телефоне и на широком окне (адаптивный шаг 1).
 *
 * Форма ограничена 420 dp и центрируется, поэтому на десктопе не растягивается
 * на всю ширину — это и фиксируется сравнением двух baseline.
 */
class AuthContentScreenshotTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun forceRussianLocale() {
            Locale.setDefault(Locale.forLanguageTag("ru"))
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun authCompactLight() {
        runDesktopComposeUiTest(width = 390, height = 844) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    AuthContent(
                        state = AuthUiState(email = "user@example.com", password = "correct horse"),
                        onIntent = {},
                        snackbarHostState = remember { SnackbarHostState() },
                    )
                }
            }
            onRoot().captureRoboImage("auth-compact-light.png")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun authWideLight() {
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    AuthContent(
                        state = AuthUiState(email = "user@example.com", password = "correct horse"),
                        onIntent = {},
                        snackbarHostState = remember { SnackbarHostState() },
                    )
                }
            }
            onRoot().captureRoboImage("auth-wide-light.png")
        }
    }
}
