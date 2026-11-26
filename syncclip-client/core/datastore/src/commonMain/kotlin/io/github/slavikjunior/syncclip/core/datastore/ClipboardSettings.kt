package io.github.slavikjunior.syncclip.core.datastore

/**
 * Лимиты локальной истории (FR-4.3) и их допустимые диапазоны.
 *
 * Значения по умолчанию — из паспорта: 10 записей, 7 дней, 10 МиБ.
 * Диапазоны: 5–200 записей, 1 час–30 дней, 1–100 МБ.
 */
public data class ClipboardSettings(
    val isOnline: Boolean = true,
    val autoInsert: Boolean = true,
    val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    val ttlHours: Int = DEFAULT_TTL_HOURS,
    val maxBytes: Long = DEFAULT_MAX_BYTES,
) {

    public companion object {
        public const val DEFAULT_MAX_ENTRIES: Int = 10
        public const val DEFAULT_TTL_HOURS: Int = 7 * 24
        public const val DEFAULT_MAX_BYTES: Long = 10L * 1024 * 1024
    }
}
