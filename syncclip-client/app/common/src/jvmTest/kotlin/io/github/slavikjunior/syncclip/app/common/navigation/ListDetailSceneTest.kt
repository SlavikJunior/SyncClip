package io.github.slavikjunior.syncclip.app.common.navigation

import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import org.junit.Test

/**
 * Проверка адаптивного шага 3: list-detail собирает `ListDetailSceneStrategy`,
 * а не экран. На широком окне видны обе панели, на узком — только верхняя
 * запись стека, а пустая деталь показывает заглушку из метаданных list-pane.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3AdaptiveApi::class)
class ListDetailSceneTest {

    @Test
    fun wideWindowShowsListAndDetailPanes() {
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent { TestNavDisplay(listOf(ListRoute, DetailRoute("1"))) }
            onNodeWithText("LIST").assertExists()
            onNodeWithText("DETAIL").assertExists()
        }
    }

    @Test
    fun compactWindowShowsOnlyTopEntry() {
        runDesktopComposeUiTest(width = 390, height = 844) {
            setContent { TestNavDisplay(listOf(ListRoute, DetailRoute("1"))) }
            onNodeWithText("DETAIL").assertExists()
            onNodeWithText("LIST").assertDoesNotExist()
        }
    }

    @Test
    fun wideWindowShowsPlaceholderUntilDetailIsOpened() {
        runDesktopComposeUiTest(width = 1280, height = 800) {
            setContent { TestNavDisplay(listOf(ListRoute)) }
            onNodeWithText("LIST").assertExists()
            onNodeWithText("PLACEHOLDER").assertExists()
            onNodeWithText("DETAIL").assertDoesNotExist()
        }
    }

    @Composable
    private fun TestNavDisplay(initialStack: List<NavKey>) {
        NavDisplay(
            backStack = mutableStateListOf<NavKey>().apply { addAll(initialStack) },
            sceneStrategies = listOf(rememberListDetailSceneStrategy<NavKey>()),
            entryProvider = entryProvider<NavKey> {
                entry<ListRoute>(
                    metadata = ListDetailSceneStrategy.listPane(
                        detailPlaceholder = { Text("PLACEHOLDER") },
                    ),
                ) {
                    Text("LIST")
                }
                entry<DetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) {
                    Text("DETAIL")
                }
            },
        )
    }

    @Serializable
    private data object ListRoute : NavKey

    @Serializable
    private data class DetailRoute(val id: String) : NavKey
}
