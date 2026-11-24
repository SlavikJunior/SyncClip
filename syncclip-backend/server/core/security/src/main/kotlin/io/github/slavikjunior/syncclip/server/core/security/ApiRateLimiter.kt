package io.github.slavikjunior.syncclip.server.core.security

import io.github.slavikjunior.syncclip.server.contracts.error.RateLimitedException
import io.github.slavikjunior.syncclip.server.core.config.LimitsConfig
import java.time.Duration

/**
 * In-memory лимитер по областям (один инстанс в MVP, §12.4). Ключи живут
 * 10 минут и выметаются планировщиком, чтобы карта не росла.
 */
class ApiRateLimiter(private val limits: LimitsConfig) {

    private val limiters: Map<RateLimitScope, KeyedRateLimiter> = buildMap {
        fun add(scope: RateLimitScope, capacity: Int, per: Duration) {
            put(scope, KeyedRateLimiter(capacity = capacity, per = per))
        }
        add(RateLimitScope.LOGIN, limits.loginPerMinute, Duration.ofMinutes(1))
        add(RateLimitScope.REGISTER, limits.registerPerMinute, Duration.ofMinutes(1))
        add(RateLimitScope.REFRESH, limits.refreshPerMinute, Duration.ofMinutes(1))
        add(RateLimitScope.LOGOUT, limits.logoutPerMinute, Duration.ofMinutes(1))
        add(RateLimitScope.CANARY, limits.canaryPerMinute, Duration.ofMinutes(1))
        add(RateLimitScope.CANARY_ATTEMPT, limits.canaryAttemptPerMinute, Duration.ofMinutes(1))
        add(RateLimitScope.GUEST_INVITE, limits.guestInvitePerHour, Duration.ofHours(1))
        add(RateLimitScope.GUEST_EXCHANGE, limits.guestExchangePerMinute, Duration.ofMinutes(1))
        add(RateLimitScope.DEFAULT, limits.defaultPerMinute, Duration.ofMinutes(1))
        add(RateLimitScope.SYNC_PENDING, limits.pendingPerMinute, Duration.ofMinutes(1))
        add(RateLimitScope.SYNC_EVENT, limits.syncEventsPerMinute, Duration.ofMinutes(1))
        add(RateLimitScope.ACK, limits.acksPerMinute, Duration.ofMinutes(1))
        add(RateLimitScope.PING, limits.pingPerMinute, Duration.ofMinutes(1))
    }

    /** Бросает 429 с Retry-After, если лимит исчерпан. */
    fun check(scope: RateLimitScope, key: String) {
        if (!limits.enabled) return
        val limiter = limiters.getValue(scope)
        if (!limiter.tryAcquire(key)) throw RateLimitedException(limiter.retryAfterSeconds())
    }

    fun tryAcquire(scope: RateLimitScope, key: String): Boolean {
        if (!limits.enabled) return true
        return limiters.getValue(scope).tryAcquire(key)
    }

    fun sweep() {
        limiters.values.forEach(KeyedRateLimiter::sweep)
    }
}
