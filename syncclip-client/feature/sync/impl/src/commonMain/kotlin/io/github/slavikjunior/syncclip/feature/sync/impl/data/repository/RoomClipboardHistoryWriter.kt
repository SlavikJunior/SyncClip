package io.github.slavikjunior.syncclip.feature.sync.impl.data.repository

import io.github.slavikjunior.syncclip.core.clipboard.EpochClock
import io.github.slavikjunior.syncclip.core.datastore.AppSettingsStore
import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettingsStore
import io.github.slavikjunior.syncclip.core.database.ClipboardEventDao
import io.github.slavikjunior.syncclip.core.database.ClipboardEventEntity
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SensitiveContentDetector
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardHistoryWriter
import kotlin.uuid.Uuid

/**
 * Запись копирования в Room с политикой вытеснения FR-4.3.
 *
 * Лимиты берутся из [ClipboardSettingsStore] на момент каждой записи, поэтому
 * изменение настроек применяется к следующему же событию. Вытесняются только
 * непривязанные записи: закреплённые не удаляются по количеству и TTL, но
 * учитываются в лимите памяти.
 *
 * US-021: если включена автоочистка чувствительных данных, запись получает
 * `expiresAtMillis = сейчас + 5 минут`. Просроченные записи удаляются при
 * следующей записи: отдельный планировщик не нужен, а данные не переживают
 * пятиминутное окно, даже если приложение закрыли сразу после копирования.
 */
internal class RoomClipboardHistoryWriter(
    private val dao: ClipboardEventDao,
    private val clock: EpochClock,
    private val settings: ClipboardSettingsStore,
    private val appSettings: AppSettingsStore? = null,
    private val newId: () -> String = { Uuid.random().toString() },
) : ClipboardHistoryWriter {

    override suspend fun save(text: String, timestampMillis: Long, sourceDeviceId: String?) {
        val nowMillis = clock.nowMillis()
        dao.upsert(
            ClipboardEventEntity(
                id = newId(),
                text = text,
                timestampMillis = timestampMillis,
                sourceDeviceId = sourceDeviceId,
                isPinned = false,
                isFavorite = false,
                expiresAtMillis = sensitiveExpiry(text, nowMillis),
            ),
        )
        evict(nowMillis)
    }

    /** Срок жизни чувствительной записи; `null` — обычная запись. */
    private suspend fun sensitiveExpiry(text: String, nowMillis: Long): Long? {
        val store = appSettings ?: return null
        // Сбой чтения настроек не должен мешать сохранить запись.
        val autoClear = runCatching { store.current().autoClearSensitive }.getOrDefault(false)
        if (!autoClear) return null
        if (!SensitiveContentDetector.isSensitive(text)) return null
        return nowMillis + SensitiveContentDetector.SENSITIVE_TTL_MILLIS
    }

    private suspend fun evict(nowMillis: Long) {
        // Просроченные чувствительные записи удаляются первыми: это обещание
        // пользователю, а не оптимизация.
        val expiredSensitive = dao.expiredSensitiveIds(nowMillis)
        if (expiredSensitive.isNotEmpty()) dao.deleteByIds(expiredSensitive)

        val limits = settings.current()
        val ttlMillis = limits.ttlHours * 60L * 60L * 1000L

        val expired = dao.expiredUnpinnedIds(nowMillis - ttlMillis)
        if (expired.isNotEmpty()) dao.deleteByIds(expired)

        val excess = dao.count("") - limits.maxEntries
        if (excess > 0) dao.deleteByIds(dao.oldestUnpinnedIds(excess))

        var overflow = dao.totalBytes() - limits.maxBytes
        while (overflow > 0) {
            // Удаляем по одной записи: партия может снести лишнее, если
            // превышение измеряется единицами байт.
            val candidates = dao.oldestUnpinnedIds(1)
            // Закреплённые записи не вытесняются, поэтому превышение может
            // остаться: лучше остановиться, чем удалять то, что просили сохранить.
            if (candidates.isEmpty()) return
            dao.deleteByIds(candidates)
            overflow = dao.totalBytes() - limits.maxBytes
        }
    }
}
