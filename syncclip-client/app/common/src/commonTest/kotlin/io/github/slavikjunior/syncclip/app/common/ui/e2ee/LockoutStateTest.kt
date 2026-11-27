package io.github.slavikjunior.syncclip.app.common.ui.e2ee

import io.github.slavikjunior.syncclip.core.datastore.MasterPasswordLockout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** FR-1.4: остаток попыток и таймер блокировки на экране разблокировки. */
internal class LockoutStateTest {

    @Test
    fun `full attempts are not shown to avoid noise`() {
        assertNull(visibleRemainingAttempts(MasterPasswordLockout(), nowMillis = 1_000))
    }

    @Test
    fun `counted attempts are shown`() {
        assertEquals(
            2,
            visibleRemainingAttempts(
                MasterPasswordLockout(remainingAttempts = 2),
                nowMillis = 1_000,
            ),
        )
        assertEquals(
            1,
            visibleRemainingAttempts(
                MasterPasswordLockout(remainingAttempts = 1),
                nowMillis = 1_000,
            ),
        )
    }

    @Test
    fun `locked state shows the timer instead of attempts`() {
        val now = 1_000_000L
        val lockout = MasterPasswordLockout(remainingAttempts = 0, lockedUntilMillis = now + 300_000)

        assertNull(visibleRemainingAttempts(lockout, now))
        assertEquals(300, lockout.remainingSeconds(now))
    }

    @Test
    fun `attempts stay hidden after the lock expires until the next failure`() {
        val now = 1_000_000L
        val lockout = MasterPasswordLockout(remainingAttempts = 0, lockedUntilMillis = now - 1)

        assertNull(visibleRemainingAttempts(lockout, now))
    }

    @Test
    fun `remaining seconds round up so the first frame shows the full lock`() {
        val now = 1_000_000L

        assertEquals(
            300,
            MasterPasswordLockout(lockedUntilMillis = now + 299_500).remainingSeconds(now),
        )
        assertEquals(
            1,
            MasterPasswordLockout(lockedUntilMillis = now + 1).remainingSeconds(now),
        )
        assertEquals(
            0,
            MasterPasswordLockout(lockedUntilMillis = now).remainingSeconds(now),
        )
    }
}
