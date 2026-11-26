package io.github.slavikjunior.syncclip.core.datastore

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** FR-1.4: состояние блокировки мастер-пароля переживает перезапуск. */
internal class E2eeLockoutPreferencesTest {

    private val dataStore = InMemoryPreferencesDataStore()
    private val store = createE2eeLockoutStore(dataStore)

    @Test
    fun `initially unlocked with full attempts`() = runTest {
        val state = store.current()

        assertEquals(3, state.remainingAttempts)
        assertFalse(state.isLocked(nowMillis = 0))
    }

    @Test
    fun `counted attempt is persisted`() = runTest {
        store.recordAttempt(remaining = 2)

        val state = store.current()
        assertEquals(2, state.remainingAttempts)
        assertFalse(state.isLocked(nowMillis = 1_000_000))
    }

    @Test
    fun `lock persists timer and remaining seconds`() = runTest {
        val now = 1_000_000L
        store.lockUntil(now + 300_000)

        val state = store.current()
        assertTrue(state.isLocked(now))
        assertEquals(300, state.remainingSeconds(now))
        assertEquals(now + 300_000, state.lockedUntilMillis)
        assertEquals(0, state.remainingAttempts)
        assertFalse(state.isLocked(now + 300_001))
    }

    @Test
    fun `success clears the state`() = runTest {
        store.lockUntil(2_000_000)
        store.clear()

        val state = store.current()
        assertEquals(MasterPasswordLockout(), state)
        assertFalse(state.isLocked(1_999_999))
    }
}
