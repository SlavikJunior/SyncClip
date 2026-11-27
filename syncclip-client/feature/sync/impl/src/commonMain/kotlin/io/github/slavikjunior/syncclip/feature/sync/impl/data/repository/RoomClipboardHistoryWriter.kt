package io.github.slavikjunior.syncclip.feature.sync.impl.data.repository

import io.github.slavikjunior.syncclip.core.clipboard.EpochClock
import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettingsStore
import io.github.slavikjunior.syncclip.core.database.ClipboardEventDao
import io.github.slavikjunior.syncclip.core.database.ClipboardEventEntity
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardHistoryWriter
import kotlin.uuid.Uuid

/**
 * Запись копирования в Room с политикой вытеснения FR-4.3.
 *
 * Лимиты берутся из [ClipboardSettingsStore] на момент каждой записи, поэтому
 * изменение настроек применяется к следующему же событию. Вытесняются только
 * непривязанные записи: закреплённые не удаляются по количеству и TTL, но
 * учитываются в лимите памяти.
 */
internal class RoomClipboardHistoryWriter(
    private val dao: ClipboardEventDao,
    private val clock: EpochClock,
    private val settings: ClipboardSettingsStore,
    private val newId: () -> String = { Uuid.random().toString() },
) : ClipboardHistoryWriter {

    override suspend fun save(text: String, timestampMillis: Long, sourceDeviceId: String?) {
        dao.upsert(
            ClipboardEventEntity(
                id = newId(),
                text = text,
                timestampMillis = timestampMillis,
                sourceDeviceId = sourceDeviceId,
                isPinned = false,
            ),
        )
        evict(clock.nowMillis())
    }

    private suspend fun evict(nowMillis: Long) {
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
