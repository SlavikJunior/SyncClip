package io.github.slavikjunior.syncclip.feature.history.impl

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeMode
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeState
import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntryType
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryDetailUiState
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryEntryUi
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryFilter
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryUiState
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.ui.HistoryContent
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.ui.HistoryDetailContent
import io.github.slavikjunior.syncclip.shared.model.DeviceIcon
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.BeforeClass
import org.junit.Test
import java.util.Locale

/**
 * Baseline History на целевых форм-факторах (адаптивный шаг 1):
 * 390 dp — телефон, 673 dp — разложенный складной (MEDIUM), 1280 dp —
 * планшет/десктоп (EXPANDED), 1920 dp — широкий десктоп.
 *
 * Двухпанельный режим собирает `ListDetailSceneStrategy`, поэтому здесь
 * снимаются отдельно список (сетка колонок) и деталь (со стрелкой на узком
 * окне и без неё на широком).
 *
 * Запуск: `./gradlew :feature:history:impl:recordRoborazziJvm`,
 * проверка — `verifyRoborazziJvm`.
 */
class HistoryContentScreenshotTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun forceRussianLocale() {
            Locale.setDefault(Locale.forLanguageTag("ru"))
        }
    }

    private val now = 1_700_000_000_000L

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun compactLight() {
        runDesktopComposeUiTest(width = 390, height = 844) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    HistoryContent(
                        state = fakeState(),
                        onIntent = {},
                        nowMillis = now,
                    )
                }
            }
            onRoot().captureRoboImage("history-compact-light.png")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun compactDark() {
        runDesktopComposeUiTest(width = 390, height = 844) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.DARK)) {
                    HistoryContent(
                        state = fakeState(),
                        onIntent = {},
                        nowMillis = now,
                    )
                }
            }
            onRoot().captureRoboImage("history-compact-dark.png")
        }
    }

    /** MEDIUM-диапазон (разложенный складной): две колонки карточек. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun foldableGridLight() {
        runDesktopComposeUiTest(width = 673, height = 841) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    HistoryContent(
                        state = fakeState(),
                        onIntent = {},
                        nowMillis = now,
                    )
                }
            }
            onRoot().captureRoboImage("history-foldable-grid-light.png")
        }
    }

    /** EXPANDED: список как левая панель list-detail (три колонки). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun wideGridLight() {
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    HistoryContent(
                        state = fakeState().copy(selectedEntryId = "1"),
                        onIntent = {},
                        nowMillis = now,
                    )
                }
            }
            onRoot().captureRoboImage("history-wide-grid-light.png")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun desktopGridLight() {
        runDesktopComposeUiTest(width = 1920, height = 1080) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    HistoryContent(
                        state = fakeState().copy(selectedEntryId = "1"),
                        onIntent = {},
                        nowMillis = now,
                    )
                }
            }
            onRoot().captureRoboImage("history-desktop-grid-light.png")
        }
    }

    /** Деталь на телефоне — отдельный экран, back-стрелка на месте. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun detailCompactLight() {
        runDesktopComposeUiTest(width = 390, height = 844) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    HistoryDetailContent(
                        state = HistoryDetailUiState(entry = firstEntry()),
                        onIntent = {},
                        nowMillis = now,
                        showBackArrow = true,
                    )
                }
            }
            onRoot().captureRoboImage("history-detail-compact-light.png")
        }
    }

    /** Деталь в правой панели: стрелки возврата нет (list-detail). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun detailPaneWideLight() {
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent {
                SyncClipTheme(theme = ThemeState(mode = ThemeMode.LIGHT)) {
                    HistoryDetailContent(
                        state = HistoryDetailUiState(entry = firstEntry()),
                        onIntent = {},
                        nowMillis = now,
                        showBackArrow = false,
                    )
                }
            }
            onRoot().captureRoboImage("history-detail-pane-light.png")
        }
    }

    private fun firstEntry(): HistoryEntryUi = HistoryEntryUi(
        id = "1",
        text = "github.com/JetBrains/compose-multiplatform",
        timestampMillis = now - 2 * 60_000L,
        sourceDeviceId = "mac",
        isPinned = true,
        sourceDeviceName = "MacBook Pro",
        sourceIcon = DeviceIcon.LAPTOP,
        type = HistoryEntryType.LINK,
    )

    private fun fakeState(): HistoryUiState = HistoryUiState(
        entries = listOf(
            firstEntry(),
            HistoryEntryUi(
                id = "2",
                text = "Check Room migrations on iOS — BundledSQLiteDriver needs its own config",
                timestampMillis = now - 12 * 60_000L,
                sourceDeviceId = "phone",
                isPinned = false,
                sourceDeviceName = "iPhone 15 Pro",
                sourceIcon = DeviceIcon.PHONE,
                type = HistoryEntryType.TEXT,
            ),
            HistoryEntryUi(
                id = "3",
                text = "val state = _state.asStateFlow()",
                timestampMillis = now - 60 * 60_000L,
                sourceDeviceId = "imac",
                isPinned = false,
                sourceDeviceName = "iMac",
                sourceIcon = DeviceIcon.DESKTOP,
                type = HistoryEntryType.CODE,
            ),
            HistoryEntryUi(
                id = "4",
                text = "Buy oat milk and olive oil",
                timestampMillis = now - 26 * 60 * 60_000L,
                sourceDeviceId = "pixel",
                isPinned = false,
                sourceDeviceName = "Pixel 9",
                sourceIcon = DeviceIcon.PHONE,
                type = HistoryEntryType.TEXT,
            ),
        ),
        filter = HistoryFilter.ALL,
        totalCount = 10,
    )
}
