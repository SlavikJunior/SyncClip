package io.github.slavikjunior.syncclip.app.common.navigation

import io.github.slavikjunior.syncclip.feature.history.api.navigation.HistoryDetailRoute
import io.github.slavikjunior.syncclip.feature.history.api.navigation.HistoryRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigatorTest {

    @Test
    fun `starts on history without back stack`() {
        val navigator = AppNavigator(HistoryRoute)

        assertEquals(HistoryRoute, navigator.current)
        assertFalse(navigator.canGoBack)
    }

    @Test
    fun `push and pop detail route`() {
        val navigator = AppNavigator(HistoryRoute)

        navigator.push(HistoryDetailRoute("event-1"))

        assertEquals(HistoryDetailRoute("event-1"), navigator.current)
        assertTrue(navigator.canGoBack)

        navigator.pop()

        assertEquals(HistoryRoute, navigator.current)
        assertFalse(navigator.canGoBack)
    }

    @Test
    fun `reset clears stack and ignores pop on root`() {
        val navigator = AppNavigator(HistoryRoute)
        navigator.push(HistoryDetailRoute("event-1"))

        navigator.pop()
        navigator.pop()

        assertEquals(HistoryRoute, navigator.current)
        assertFalse(navigator.canGoBack)

        navigator.push(HistoryDetailRoute("event-2"))
        navigator.reset(HistoryRoute)

        assertEquals(HistoryRoute, navigator.current)
        assertFalse(navigator.canGoBack)
    }
}
