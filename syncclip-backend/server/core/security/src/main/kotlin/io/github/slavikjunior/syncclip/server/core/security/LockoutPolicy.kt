package io.github.slavikjunior.syncclip.server.core.security

import java.time.Duration
import java.time.Instant

/** Состояние блокировки мастер-пароля (FR-1.4), хранится в master_password_locks. */
data class LockState(
    val failedInGroup: Int = 0,
    val lockoutCount: Int = 0,
    val lockedUntil: Instant? = null,
)

sealed interface LockVerdict {
    data object Allowed : LockVerdict
    data class Blocked(val retryAfter: Duration) : LockVerdict
}

sealed interface FailureOutcome {
    data class Counted(val remainingInGroup: Int, val state: LockState) : FailureOutcome
    data class Locked(val state: LockState, val retryAfter: Duration) : FailureOutcome
}

/**
 * Чистая state machine §7.5:
 * 3 неудачи → блокировка; длительность D(k) = 2 ч при k % 3 == 2, иначе 5 мин
 * (последовательность 5 м, 5 м, 2 ч, 5 м, 5 м, 2 ч, …).
 */
class LockoutPolicy(
    private val groupSize: Int = 3,
    private val shortLock: Duration = Duration.ofMinutes(5),
    private val longLock: Duration = Duration.ofHours(2),
) {

    init {
        require(groupSize >= 1) { "groupSize must be >= 1" }
    }

    fun check(state: LockState, now: Instant): LockVerdict {
        val until = state.lockedUntil ?: return LockVerdict.Allowed
        if (!until.isAfter(now)) return LockVerdict.Allowed
        return LockVerdict.Blocked(Duration.between(now, until))
    }

    /** Попытка при активной блокировке не инкрементирует счётчик (таблица §7.5). */
    fun onFailure(state: LockState, now: Instant): FailureOutcome {
        val active = check(state, now)
        if (active is LockVerdict.Blocked) {
            return FailureOutcome.Locked(currentLockedState(state), active.retryAfter)
        }
        val base = if (state.lockedUntil != null) {
            // Блокировка истекла: счётчик группы уже сброшен при её выставлении.
            state.copy(lockedUntil = null, failedInGroup = 0)
        } else {
            state
        }
        val nextFailed = base.failedInGroup + 1
        if (nextFailed < groupSize) {
            return FailureOutcome.Counted(
                remainingInGroup = groupSize - nextFailed,
                state = base.copy(failedInGroup = nextFailed),
            )
        }
        val duration = lockDuration(base.lockoutCount)
        val lockedUntil = now.plus(duration)
        return FailureOutcome.Locked(
            state = base.copy(
                failedInGroup = 0,
                lockoutCount = base.lockoutCount + 1,
                lockedUntil = lockedUntil,
            ),
            retryAfter = duration,
        )
    }

    /** Успешная попытка полностью сбрасывает и счётчик, и цикл блокировок (FR-1.4). */
    fun onSuccess(): LockState = LockState()

    fun remainingAttempts(state: LockState, now: Instant): Int {
        if (check(state, now) is LockVerdict.Blocked) return 0
        return (groupSize - state.failedInGroup).coerceAtLeast(0)
    }

    fun lockDuration(lockoutCount: Int): Duration =
        if (lockoutCount % 3 == 2) longLock else shortLock

    private fun currentLockedState(state: LockState): LockState =
        state.copy(lockedUntil = state.lockedUntil)

    companion object {
        fun default(): LockoutPolicy = LockoutPolicy(
            groupSize = 3,
            shortLock = Duration.ofMinutes(5),
            longLock = Duration.ofHours(2),
        )
    }
}
