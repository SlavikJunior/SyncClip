package io.github.slavikjunior.syncclip.feature.sync.impl

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeMode
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeState
import io.github.slavikjunior.syncclip.feature.sync.impl.presentation.state.SyncDeviceUi
import io.github.slavikjunior.syncclip.feature.sync.impl.presentation.state.SyncStatusUiState
import io.github.slavikjunior.syncclip.feature.sync.impl.presentation.ui.SyncStatusContent
import io.github.slavikjunior.syncclip.shared.model.DeviceIcon
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.BeforeClass
import org.junit.Test
import java.util.Locale

class SyncStatusContentScreenshotTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun forceRussianLocale() {
            Locale.setDefault(Locale.forLanguageTag("ru"))
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun syncLight() {
        runDesktopComposeUiTest(width = 390, height = 844) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    SyncStatusContent(state = fakeState(), onIntent = {})
                }
            }
            onRoot().captureRoboImage("sync-light.png")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun syncDark() {
        runDesktopComposeUiTest(width = 390, height = 844) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.DARK)) {
                    SyncStatusContent(state = fakeState(), onIntent = {})
                }
            }
            onRoot().captureRoboImage("sync-dark.png")
        }
    }

    /** MEDIUM-диапазон (складной/планшет в портрете). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun syncMedium() {
        runDesktopComposeUiTest(width = 673, height = 841) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    SyncStatusContent(state = fakeState(), onIntent = {})
                }
            }
            onRoot().captureRoboImage("sync-medium.png")
        }
    }

    /** EXPANDED: контент ограничен 840 dp и центрирован. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun syncWide() {
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    SyncStatusContent(state = fakeState(), onIntent = {})
                }
            }
            onRoot().captureRoboImage("sync-wide.png")
        }
    }

    private fun fakeState(): SyncStatusUiState = SyncStatusUiState(
        isOnline = true,
        packetCount = 3,
        lastText = "github.com/JetBrains/compose-multiplatform",
        devices = listOf(
            SyncDeviceUi("1", "MacBook Pro", DeviceIcon.LAPTOP, isCurrent = true),
            SyncDeviceUi("2", "iPhone 15 Pro", DeviceIcon.PHONE),
            SyncDeviceUi("3", "Pixel 9", DeviceIcon.PHONE),
        ),
    )
}
