package io.github.slavikjunior.syncclip.server.core.security

import java.time.Duration
import java.time.Instant

/**
 * Token bucket с ленивым пополнением. Потокобезопасен: мутация под lock,
 * расчёт — на наносекундах монотонного времени.
 */
class TokenBucket(
    val capacity: Int,
    refillPerSecond: Double,
    private var nowNanos: () -> Long = System::nanoTime,
) {
    private val refillPerNano = refillPerSecond / 1_000_000_000.0
    private var tokens: Double = capacity.toDouble()
    private var lastRefill: Long = nowNanos()

    @Synchronized
    fun tryAcquire(count: Int = 1): Boolean {
        refill()
        if (tokens < count) return false
        tokens -= count
        return true
    }

    @Synchronized
    fun available(): Int {
        refill()
        return tokens.toInt()
    }

    private fun refill() {
        val now = nowNanos()
        if (now > lastRefill) {
            tokens = (tokens + (now - lastRefill) * refillPerNano).coerceAtMost(capacity.toDouble())
            lastRefill = now
        }
    }
}

/**
 * Лимитер по ключу (IP, userId, deviceId). Ключи живут TTL и выметаются,
 * чтобы карта не росла бесконечно (дизайн §12.4).
 */
class KeyedRateLimiter(
    private val capacity: Int,
    private val per: Duration,
    private val keyTtl: Duration = Duration.ofMinutes(10),
    private val clock: () -> Instant = Instant::now,
) {
    private data class Entry(val bucket: TokenBucket, val createdAt: Instant)

    private val entries = java.util.concurrent.ConcurrentHashMap<String, Entry>()

    fun tryAcquire(key: String): Boolean {
        val safeCapacity = capacity.coerceAtLeast(1)
        val entry = entries.computeIfAbsent(key) {
            Entry(
                TokenBucket(safeCapacity, safeCapacity / per.seconds.toDouble().coerceAtLeast(1.0)),
                clock(),
            )
        }
        return entry.bucket.tryAcquire()
    }

    /** Секунды до следующей попытки — для `Retry-After` (приблизительно, 1 тик). */
    fun retryAfterSeconds(): Long = (per.seconds / capacity.coerceAtLeast(1)).coerceAtLeast(1)

    fun sweep() {
        val threshold = clock().minus(keyTtl)
        entries.entries.removeIf { it.value.createdAt.isBefore(threshold) }
    }

    fun size(): Int = entries.size

}
