package io.github.slavikjunior.syncclip.feature.sync.impl.data.transport

/**
 * Экспоненциальный backoff из FR-2.3: `min(1s * 2^n, 30s)`.
 *
 * `attempt` — номер подряд идущей неудачи, начиная с 1. Пятая неудача
 * переводит транспорт в [SyncConnectionStatus.SUSPENDED], но попытки не
 * прекращаются: сеть может вернуться сама.
 */
internal class BackoffPolicy(
    private val initialDelayMillis: Long = 1_000,
    private val maxDelayMillis: Long = 30_000,
    val maxAttempts: Int = 5,
) {

    fun delayForAttempt(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, MAX_EXPONENT)
        return (initialDelayMillis shl exponent).coerceAtMost(maxDelayMillis)
    }

    private companion object {
        /** 2^5 * 1s = 32s уже выше потолка, дальше расти некуда. */
        const val MAX_EXPONENT = 5
    }
}
