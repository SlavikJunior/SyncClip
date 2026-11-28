package io.github.slavikjunior.syncclip.feature.history.impl.presentation.state

import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntry
import io.github.slavikjunior.syncclip.feature.history.impl.domain.repository.HistoryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Репозиторий истории, у которого каждый источник задаётся тестом. */
internal class FakeHistoryRepository(
    private val cached: List<HistoryEntry>? = null,
    private val page: List<HistoryEntry> = emptyList(),
    private val pageFlow: (() -> Flow<List<HistoryEntry>>)? = null,
    private val cachedGate: CompletableDeferred<Unit>? = null,
) : HistoryRepository {

    override fun observePage(query: String, limit: Int, offset: Int): Flow<List<HistoryEntry>> =
        pageFlow?.invoke() ?: flowOf(page)

    override suspend fun count(query: String): Int = page.size

    override suspend fun findById(id: String): HistoryEntry? =
        (cached.orEmpty() + page).firstOrNull { it.id == id }

    override suspend fun cachedFirstPage(limit: Int): List<HistoryEntry>? {
        cachedGate?.await()
        return cached
    }

    override suspend fun delete(id: String) = Unit

    override suspend fun restore(entry: HistoryEntry) = Unit

    override suspend fun setPinned(id: String, pinned: Boolean) = Unit

    override suspend fun setFavorite(id: String, favorite: Boolean) = Unit

    override suspend fun clearExpiredSensitive(nowMillis: Long): Int = 0

    var lastEvictionCutoff: Long? = null
    var lastEvictionMaxCount: Int? = null

    override suspend fun evictExpired(cutoffMillis: Long, maxCount: Int): Int {
        lastEvictionCutoff = cutoffMillis
        lastEvictionMaxCount = maxCount
        return 0
    }
}
