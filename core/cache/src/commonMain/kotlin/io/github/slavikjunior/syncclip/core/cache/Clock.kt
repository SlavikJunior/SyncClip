package io.github.slavikjunior.syncclip.core.cache

/**
 * Источник текущего времени для TTL-решений кэша.
 *
 * Абстракция нужна тестам: срок жизни записи проверяется сдвигом часов, а не
 * `delay`. В рабочем коде используется [Clock.System].
 */
public fun interface Clock {

    /** Текущее время в миллисекундах от эпохи. */
    public fun nowMillis(): Long

    public companion object {
        /** Часы операционной системы. */
        public val System: Clock = Clock { kotlin.time.Clock.System.now().toEpochMilliseconds() }
    }
}
