package io.github.slavikjunior.syncclip.app.common.ui.e2ee

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeMode
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeState
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.BeforeClass
import org.junit.Test
import java.util.Locale

/**
 * Baseline экрана разблокировки E2EE на телефоне и на широком окне
 * (адаптивный шаг 1): форма ограничена 520 dp, поэтому на десктопе поле
 * мастер-пароля не растягивается на всю ширину.
 */
class E2eeUnlockContentScreenshotTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun forceRussianLocale() {
            Locale.setDefault(Locale.forLanguageTag("ru"))
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun unlockCompactLight() {
        runDesktopComposeUiTest(width = 390, height = 844) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    E2eeUnlockContent(
                        password = "correct horse",
                        isSubmitting = false,
                        lockedSeconds = 0,
                        remainingAttempts = 2,
                        error = null,
                        onPasswordChange = {},
                        onSubmit = {},
                    )
                }
            }
            onRoot().captureRoboImage("e2ee-unlock-compact-light.png")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun unlockWideLight() {
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    E2eeUnlockContent(
                        password = "correct horse",
                        isSubmitting = false,
                        lockedSeconds = 0,
                        remainingAttempts = 2,
                        error = null,
                        onPasswordChange = {},
                        onSubmit = {},
                    )
                }
            }
            onRoot().captureRoboImage("e2ee-unlock-wide-light.png")
        }
    }
}
