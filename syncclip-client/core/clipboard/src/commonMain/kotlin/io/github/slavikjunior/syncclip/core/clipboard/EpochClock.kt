package io.github.slavikjunior.syncclip.core.clipboard

/**
 * Часы для окон эхо-защиты и меток копирования.
 *
 * Абстракция нужна тестам: окна проверяются сдвигом времени, а не `delay`.
 */
public fun interface EpochClock {

    /** Текущее время в миллисекундах от эпохи. */
    public fun nowMillis(): Long

    public companion object {
        /** Часы операционной системы. */
        public val System: EpochClock =
            EpochClock { kotlin.time.Clock.System.now().toEpochMilliseconds() }
    }
}
