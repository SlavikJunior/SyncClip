package io.github.slavikjunior.syncclip.server.core.security

import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RateLimiterTest {

    @Test
    fun `bucket allows capacity and refills over time`() {
        val nanos = AtomicLong(0)
        val bucket = TokenBucket(capacity = 2, refillPerSecond = 1.0, nowNanos = { nanos.get() })
        assertTrue(bucket.tryAcquire())
        assertTrue(bucket.tryAcquire())
        assertFalse(bucket.tryAcquire())

        nanos.addAndGet(1_000_000_000) // 1 second
        assertTrue(bucket.tryAcquire())
        assertFalse(bucket.tryAcquire())
    }

    @Test
    fun `keyed limiter isolates keys`() {
        val limiter = KeyedRateLimiter(capacity = 1, per = Duration.ofMinutes(1))
        assertTrue(limiter.tryAcquire("a"))
        assertFalse(limiter.tryAcquire("a"))
        assertTrue(limiter.tryAcquire("b"))
        assertEquals(2, limiter.size())
    }

    @Test
    fun `retry after is at least one second`() {
        val limiter = KeyedRateLimiter(capacity = 100, per = Duration.ofMinutes(1))
        assertEquals(1, limiter.retryAfterSeconds())
    }
}
