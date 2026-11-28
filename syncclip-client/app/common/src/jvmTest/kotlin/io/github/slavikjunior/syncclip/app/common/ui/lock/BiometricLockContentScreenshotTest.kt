package io.github.slavikjunior.syncclip.app.common.ui.lock

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
 * Baseline экрана биометрической блокировки (US-022, адаптивный шаг 1).
 *
 * Телефон — компактная колонка с ошибкой разблокировки, широкое окно —
 * подсказка по центру: форма не растягивается, потому что контент ограничен
 * по ширине самим экраном.
 *
 * Запуск: `./gradlew :app:common:recordRoborazziJvm`,
 * проверка — `./gradlew :app:common:verifyRoborazziJvm`.
 */
class BiometricLockContentScreenshotTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun forceRussianLocale() {
            Locale.setDefault(Locale.forLanguageTag("ru"))
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun compactFailedLight() {
        runDesktopComposeUiTest(width = 390, height = 844) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    BiometricLockContent(failed = true, onUnlock = {})
                }
            }
            onRoot().captureRoboImage("biometric-lock-compact-light.png")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun wideHintLight() {
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    BiometricLockContent(failed = false, onUnlock = {})
                }
            }
            onRoot().captureRoboImage("biometric-lock-wide-light.png")
        }
    }
}
