package io.github.slavikjunior.syncclip.app.common.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.github.slavikjunior.syncclip.app.common.ui.AppSection
import io.github.slavikjunior.syncclip.feature.auth.api.navigation.AuthRoute
import io.github.slavikjunior.syncclip.feature.history.api.navigation.HistoryDetailRoute
import io.github.slavikjunior.syncclip.feature.history.api.navigation.HistoryRoute
import io.github.slavikjunior.syncclip.feature.settings.api.navigation.SettingsRoute
import io.github.slavikjunior.syncclip.feature.sync.api.navigation.SyncStatusRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json

class AppNavigationTest {

    @Test
    fun `section root routes belong to their section`() {
        for (section in AppSection.entries) {
            assertEquals(section, sectionOf(section.rootRoute()))
        }
    }

    @Test
    fun `history detail belongs to history section`() {
        assertEquals(AppSection.HISTORY, sectionOf(HistoryDetailRoute("event-1")))
    }

    @Test
    fun `resetTo replaces whole stack with new root`() {
        val stack = NavBackStack<NavKey>(HistoryRoute, HistoryDetailRoute("event-1"))

        stack.resetTo(SettingsRoute)

        assertEquals(listOf(SettingsRoute), stack.toList())
    }

    @Test
    fun `resetTo keeps single root unchanged`() {
        val stack = NavBackStack<NavKey>(HistoryRoute)

        stack.resetTo(HistoryRoute)

        assertEquals(listOf(HistoryRoute), stack.toList())
    }

    /**
     * F14 аудита Nav3: повторный тап по карточке не должен добавлять второй
     * экземпляр того же маршрута — иначе back требует двух нажатий.
     */
    @Test
    fun `push ignores duplicate of the current route`() {
        val stack = NavBackStack<NavKey>(HistoryRoute)

        stack.push(HistoryDetailRoute("event-1"))
        stack.push(HistoryDetailRoute("event-1"))

        assertEquals(listOf(HistoryRoute, HistoryDetailRoute("event-1")), stack.toList())
    }

    @Test
    fun `push adds a different route after detail`() {
        val stack = NavBackStack<NavKey>(HistoryRoute, HistoryDetailRoute("event-1"))

        stack.push(HistoryDetailRoute("event-2"))

        assertEquals(
            listOf(HistoryRoute, HistoryDetailRoute("event-1"), HistoryDetailRoute("event-2")),
            stack.toList(),
        )
    }

    /**
     * Восстановление стека после смерти процесса идёт через полиморфную
     * десериализацию: каждый ключ из реестра обязан пережить round-trip.
     */
    @Test
    fun `every registered route survives polymorphic round-trip`() {
        val json = Json { serializersModule = AppNavSavedState.serializersModule }
        val serializer = PolymorphicSerializer(NavKey::class)
        val routes: List<NavKey> = listOf(
            HistoryRoute,
            HistoryDetailRoute("event-1"),
            SyncStatusRoute,
            SettingsRoute,
            AuthRoute,
        )

        for (route in routes) {
            val encoded = json.encodeToString(serializer, route)
            assertEquals(route, json.decodeFromString(serializer, encoded))
        }
    }
}
