package io.github.slavikjunior.syncclip.feature.history.impl.data.repository

import io.github.slavikjunior.syncclip.core.database.ClipboardEventDao
import io.github.slavikjunior.syncclip.feature.history.impl.data.cache.HistoryPageCache
import io.github.slavikjunior.syncclip.feature.history.impl.data.mapper.toDomain
import io.github.slavikjunior.syncclip.feature.history.impl.data.mapper.toEntity
import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntry
import io.github.slavikjunior.syncclip.feature.history.impl.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

internal class RoomHistoryRepository(
    private val dao: ClipboardEventDao,
    private val pageCache: HistoryPageCache,
) : HistoryRepository {

    override fun observePage(query: String, limit: Int, offset: Int): Flow<List<HistoryEntry>> =
        dao.observeSearchPage(query, limit, offset)
            .map { entities -> entities.map { it.toDomain() } }
            .onEach { page ->
                if (query.isEmpty() && offset == 0) {
                    pageCache.putFirstPage(limit, page)
                }
            }

    override suspend fun cachedFirstPage(limit: Int): List<HistoryEntry>? = pageCache.firstPage(limit)

    override suspend fun count(query: String): Int = dao.count(query)

    override suspend fun findById(id: String): HistoryEntry? = dao.findById(id)?.toDomain()

    override suspend fun delete(id: String) {
        dao.delete(id)
        pageCache.invalidate()
    }

    override suspend fun restore(entry: HistoryEntry) {
        dao.upsert(entry.toEntity())
        pageCache.invalidate()
    }

    override suspend fun setPinned(id: String, pinned: Boolean) {
        dao.setPinned(id, pinned)
        pageCache.invalidate()
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        dao.setFavorite(id, favorite)
        pageCache.invalidate()
    }

    override suspend fun clearExpiredSensitive(nowMillis: Long): Int {
        val ids = dao.expiredSensitiveIds(nowMillis)
        if (ids.isEmpty()) return 0
        dao.deleteByIds(ids)
        pageCache.invalidate()
        return ids.size
    }

    override suspend fun evictExpired(cutoffMillis: Long, maxCount: Int): Int {
        val ids = dao.expiredUnpinnedIds(cutoffMillis).toMutableSet()
        val total = dao.count("")
        if (total > maxCount) {
            // LRU-вытеснение только непривязанных записей; число кандидатов
            // ограничено сверху, чтобы не читать всю историю в память.
            ids += dao.oldestUnpinnedIds((total - maxCount).coerceAtMost(EVICTION_BATCH))
        }
        dao.deleteByIds(ids.toList())
        if (ids.isNotEmpty()) {
            pageCache.invalidate()
        }
        return ids.size
    }

    private companion object {
        const val EVICTION_BATCH = 200
    }
}
