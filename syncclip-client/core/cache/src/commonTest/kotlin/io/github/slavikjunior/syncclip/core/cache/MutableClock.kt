package io.github.slavikjunior.syncclip.core.cache

import kotlin.time.Duration

/** Часы, которые двигаются только по команде теста. */
internal class MutableClock(initialTimeMillis: Long = 0L) : Clock {

    private var currentTimeMillis: Long = initialTimeMillis

    override fun nowMillis(): Long = currentTimeMillis

    fun advance(amount: Duration) {
        currentTimeMillis += amount.inWholeMilliseconds
    }
}
