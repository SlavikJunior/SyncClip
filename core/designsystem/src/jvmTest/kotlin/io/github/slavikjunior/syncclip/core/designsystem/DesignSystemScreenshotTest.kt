package io.github.slavikjunior.syncclip.core.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.slavikjunior.syncclip.core.designsystem.component.DesignSystemGallery
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeMode
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeState
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

/**
 * Baseline дизайн-системы. Запуск:
 * `./gradlew :core:designsystem:recordRoborazziJvm` — записать,
 * `./gradlew :core:designsystem:verifyRoborazziJvm` — проверить.
 */
class DesignSystemScreenshotTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun galleryLight() {
        runDesktopComposeUiTest(width = 900, height = 1800) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    DesignSystemGallery()
                }
            }
            onRoot().captureRoboImage("gallery-light.png")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun galleryDark() {
        runDesktopComposeUiTest(width = 900, height = 1800) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.DARK)) {
                    DesignSystemGallery()
                }
            }
            onRoot().captureRoboImage("gallery-dark.png")
        }
    }
}
