package io.github.slavikjunior.syncclip.app.common.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.junit.Test

/**
 * Рецепт Nav3 «multiple back stacks» (F5 аудита): у каждого раздела свой
 * сохраняемый стек, поэтому переключение вкладки не теряет открытую деталь.
 *
 * Тест повторяет раскладку `AppLayout` на двух разделах и проверяет именно
 * поведение `NavDisplay` при подмене `backStack`, а не только хелперы.
 */
@OptIn(ExperimentalTestApi::class)
class MultipleBackStacksTest {

    @Test
    fun `switching section keeps its navigation history`() {
        runDesktopComposeUiTest(width = 390, height = 844) {
            setContent { Harness() }

            // Раздел A: открываем деталь.
            onNodeWithTag(OPEN_TAG).performClick()
            onNodeWithText("DETAIL_A").assertExists()

            // Уходим в раздел B и обратно.
            onNodeWithTag(SWITCH_TAG).performClick()
            onNodeWithText("ROOT_B").assertExists()
            onNodeWithTag(SWITCH_TAG).performClick()

            // Деталь раздела A сохранилась: стек раздела не сброшен.
            onNodeWithText("DETAIL_A").assertExists()
        }
    }

    @Composable
    private fun Harness() {
        val section = remember { mutableStateOf(0) }
        val rootA = rememberNavBackStack(TestSavedState, RootA)
        val rootB = rememberNavBackStack(TestSavedState, RootB)
        val stack = if (section.value == 0) rootA else rootB

        Column {
            Button(
                onClick = { section.value = 1 - section.value },
                modifier = Modifier.testTag(SWITCH_TAG),
            ) {
                Text("Switch")
            }
            Button(
                onClick = { stack.push(DetailA("1")) },
                modifier = Modifier.testTag(OPEN_TAG),
            ) {
                Text("Open")
            }
            NavDisplay(
                backStack = stack,
                entryProvider = entryProvider<NavKey> {
                    entry<RootA> { Text("ROOT_A") }
                    entry<DetailA> { Text("DETAIL_A") }
                    entry<RootB> { Text("ROOT_B") }
                },
            )
        }
    }

    @Serializable
    private data object RootA : NavKey

    @Serializable
    private data class DetailA(val id: String) : NavKey

    @Serializable
    private data object RootB : NavKey

    private companion object {
        const val SWITCH_TAG = "switch_section"
        const val OPEN_TAG = "open_detail"

        /**
         * Полиморфная регистрация ключей: `rememberNavBackStack` сохраняет стек
         * только для зарегистрированных подтипов `NavKey`.
         */
        val TestSavedState = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(RootA::class, RootA.serializer())
                    subclass(DetailA::class, DetailA.serializer())
                    subclass(RootB::class, RootB.serializer())
                }
            }
        }
    }
}
