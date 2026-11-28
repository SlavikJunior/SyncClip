package io.github.slavikjunior.syncclip.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIconKind
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipNavigationItem
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipNavigationScaffold
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeMode
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeState
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

/**
 * Baseline адаптивного каркаса навигации: COMPACT — нижняя панель,
 * MEDIUM/EXPANDED — боковой rail. Запуск:
 * `./gradlew :core:designsystem:recordRoborazziJvm`,
 * проверка — `./gradlew :core:designsystem:verifyRoborazziJvm`.
 */
class NavigationScaffoldScreenshotTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun compactShowsBar() {
        runDesktopComposeUiTest(width = 390, height = 844) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    SyncClipNavigationScaffold(
                        items = NavigationItems,
                        selectedIndex = 0,
                        onSelect = {},
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                        )
                    }
                }
            }
            onRoot().captureRoboImage("navigation-compact-light.png")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun expandedShowsRail() {
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    SyncClipNavigationScaffold(
                        items = NavigationItems,
                        selectedIndex = 0,
                        onSelect = {},
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                        )
                    }
                }
            }
            onRoot().captureRoboImage("navigation-expanded-light.png")
        }
    }

    private companion object {
        val NavigationItems = listOf(
            SyncClipNavigationItem(SyncClipIconKind.HISTORY, "История"),
            SyncClipNavigationItem(SyncClipIconKind.SYNC, "Синхронизация"),
            SyncClipNavigationItem(SyncClipIconKind.SETTINGS, "Настройки"),
        )
    }
}
