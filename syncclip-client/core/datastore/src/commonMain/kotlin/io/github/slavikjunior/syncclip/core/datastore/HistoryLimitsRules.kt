package io.github.slavikjunior.syncclip.core.datastore

/**
 * Правила допустимых значений истории (FR-4.3).
 *
 * Валидация живёт рядом с моделью, чтобы UI и хранилище проверяли одно и то же:
 * сервер настроек не касается, значит границы не должны расходиться.
 */
public object HistoryLimitsRules {

    public const val MIN_ENTRIES: Int = 5
    public const val MAX_ENTRIES: Int = 200

    public const val MIN_TTL_HOURS: Int = 1
    public const val MAX_TTL_HOURS: Int = 30 * 24

    public const val MIN_BYTES: Long = 1L * 1024 * 1024
    public const val MAX_BYTES: Long = 100L * 1024 * 1024

    public fun isValidEntries(value: Int): Boolean = value in MIN_ENTRIES..MAX_ENTRIES

    public fun isValidTtlHours(value: Int): Boolean = value in MIN_TTL_HOURS..MAX_TTL_HOURS

    public fun isValidBytes(value: Long): Boolean = value in MIN_BYTES..MAX_BYTES

    /** Лимиты TTL/объёма/количества одновременно. */
    public fun validate(maxEntries: Int, ttlHours: Int, maxBytes: Long): Boolean =
        isValidEntries(maxEntries) && isValidTtlHours(ttlHours) && isValidBytes(maxBytes)
}
