package io.github.slavikjunior.syncclip.core.datastore

import kotlinx.coroutines.flow.Flow

/**
 * Хранилище настроек синхронизации и истории (FR-2.9, FR-2.10, FR-4.3).
 *
 * [state] — поток изменений для UI, [current] — синхронный снимок для конвейера
 * захвата, которому значение нужно в момент события, а не при следующей эмиссии.
 */
public interface ClipboardSettingsStore {

    public val state: Flow<ClipboardSettings>

    public suspend fun current(): ClipboardSettings

    /** FR-2.9: Offline останавливает приём и отправку, история продолжает писаться. */
    public suspend fun setOnline(isOnline: Boolean)

    /** FR-2.10: вставлять ли удалённый пакет в системный буфер. */
    public suspend fun setAutoInsert(autoInsert: Boolean)

    /** FR-4.3: 5–200 записей. @return `false`, если значение вне диапазона. */
    public suspend fun setMaxEntries(value: Int): Boolean

    /** FR-4.3: TTL 1 час – 30 дней. @return `false`, если значение вне диапазона. */
    public suspend fun setTtlHours(value: Int): Boolean

    /** FR-4.3: 1–100 МБ. @return `false`, если значение вне диапазона. */
    public suspend fun setMaxBytes(value: Long): Boolean

    /** Применяет все три лимита атомарно; при невалидном значении — `false`. */
    public suspend fun setHistoryLimits(
        maxEntries: Int,
        ttlHours: Int,
        maxBytes: Long,
    ): Boolean
}
