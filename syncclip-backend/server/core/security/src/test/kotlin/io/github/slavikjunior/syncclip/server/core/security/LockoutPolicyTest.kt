package io.github.slavikjunior.syncclip.server.core.security

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Таблица переходов §7.5: 3/5м, 3/5м, 3/2ч, цикл. */
class LockoutPolicyTest {

    private val policy = LockoutPolicy(
        groupSize = 3,
        shortLock = Duration.ofMinutes(5),
        longLock = Duration.ofHours(2),
    )
    private val now: Instant = Instant.parse("2026-11-23T10:00:00Z")

    @Test
    fun `first two failures are counted`() {
        val first = policy.onFailure(LockState(), now)
        val counted = assertIs<FailureOutcome.Counted>(first)
        assertEquals(2, counted.remainingInGroup)
        assertEquals(1, counted.state.failedInGroup)

        val second = assertIs<FailureOutcome.Counted>(policy.onFailure(counted.state, now))
        assertEquals(1, second.remainingInGroup)
        assertEquals(2, second.state.failedInGroup)
    }

    @Test
    fun `third failure locks for five minutes`() {
        val state = LockState(failedInGroup = 2)
        val locked = assertIs<FailureOutcome.Locked>(policy.onFailure(state, now))
        assertEquals(Duration.ofMinutes(5), locked.retryAfter)
        assertEquals(0, locked.state.failedInGroup)
        assertEquals(1, locked.state.lockoutCount)
        assertEquals(now.plus(Duration.ofMinutes(5)), locked.state.lockedUntil)
    }

    @Test
    fun `lock durations follow 5m 5m 2h sequence`() {
        assertEquals(Duration.ofMinutes(5), policy.lockDuration(0))
        assertEquals(Duration.ofMinutes(5), policy.lockDuration(1))
        assertEquals(Duration.ofHours(2), policy.lockDuration(2))
        assertEquals(Duration.ofMinutes(5), policy.lockDuration(3))
        assertEquals(Duration.ofHours(2), policy.lockDuration(5))
    }

    @Test
    fun `attempt during active lock does not increment counter`() {
        val lockedState = LockState(failedInGroup = 0, lockoutCount = 1, lockedUntil = now.plus(Duration.ofMinutes(5)))
        val outcome = assertIs<FailureOutcome.Locked>(policy.onFailure(lockedState, now.plusSeconds(60)))
        assertEquals(lockedState.fingerprint(), outcome.state.fingerprint())
        assertEquals(Duration.ofMinutes(4), outcome.retryAfter)
    }

    @Test
    fun `check returns remaining time while locked and allowed after expiry`() {
        val locked = LockState(lockedUntil = now.plusSeconds(120))
        val verdict = assertIs<LockVerdict.Blocked>(policy.check(locked, now))
        assertEquals(Duration.ofSeconds(120), verdict.retryAfter)
        assertIs<LockVerdict.Allowed>(policy.check(locked, now.plusSeconds(121)))
    }

    @Test
    fun `failure after lock expiry starts a new group`() {
        val expired = LockState(failedInGroup = 0, lockoutCount = 2, lockedUntil = now.minusSeconds(1))
        val outcome = assertIs<FailureOutcome.Counted>(policy.onFailure(expired, now))
        assertEquals(1, outcome.state.failedInGroup)
        assertEquals(2, outcome.state.lockoutCount)
        assertEquals(2, outcome.remainingInGroup)
    }

    @Test
    fun `success resets counters and cycle`() {
        val state = policy.onSuccess()
        assertEquals(LockState(), state)
        assertTrue(state.lockedUntil == null)
        assertFalse(policy.check(state, now) is LockVerdict.Blocked)
    }

    private fun LockState.fingerprint(): String = "$failedInGroup/$lockoutCount/$lockedUntil"
}
