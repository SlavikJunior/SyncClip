package io.github.slavikjunior.syncclip.feature.history.impl.domain.repository

import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntry
import kotlinx.coroutines.flow.Flow

internal interface HistoryRepository {
    fun observePage(query: String, limit: Int, offset: Int): Flow<List<HistoryEntry>>
    suspend fun count(query: String): Int
    suspend fun findById(id: String): HistoryEntry?

    /**
     * Первая страница без поискового запроса из памяти — для мгновенной
     * отрисовки до первого ответа Room. `null`, если снимка нет.
     */
    suspend fun cachedFirstPage(limit: Int): List<HistoryEntry>?

    /** Удаление только локальное: сервер не получает tombstones. */
    suspend fun delete(id: String)
    suspend fun restore(entry: HistoryEntry)
    suspend fun setPinned(id: String, pinned: Boolean)

    /** US-017: избранное. */
    suspend fun setFavorite(id: String, favorite: Boolean)

    /** Вытеснение только непривязанных записей, по LRU и TTL. */
    suspend fun evictExpired(cutoffMillis: Long, maxCount: Int): Int

    /**
     * US-021: удаление чувствительных записей с истёкшим сроком.
     *
     * @return сколько записей удалено.
     */
    suspend fun clearExpiredSensitive(nowMillis: Long): Int
}
