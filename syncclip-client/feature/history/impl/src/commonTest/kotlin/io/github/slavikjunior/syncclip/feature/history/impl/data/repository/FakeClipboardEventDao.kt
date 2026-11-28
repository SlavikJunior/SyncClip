package io.github.slavikjunior.syncclip.feature.history.impl.data.repository

import io.github.slavikjunior.syncclip.core.database.ClipboardEventDao
import io.github.slavikjunior.syncclip.core.database.ClipboardEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * DAO истории в памяти.
 *
 * Фильтрация и пагинация повторяют SQL из [ClipboardEventDao], поэтому тест
 * проверяет репозиторий, а не поведение Room.
 */
internal class FakeClipboardEventDao(
    initial: List<ClipboardEventEntity> = emptyList(),
) : ClipboardEventDao {

    private val events = MutableStateFlow(initial)

    fun publish(items: List<ClipboardEventEntity>) {
        events.value = items
    }

    override fun observePage(limit: Int, offset: Int): Flow<List<ClipboardEventEntity>> =
        events.map { items -> items.sortedWith(ORDER).drop(offset).take(limit) }

    override fun observeSearchPage(
        query: String,
        limit: Int,
        offset: Int,
    ): Flow<List<ClipboardEventEntity>> = events.map { items ->
        items.filter { query.isEmpty() || it.text.contains(query, ignoreCase = true) }
            .sortedWith(ORDER)
            .drop(offset)
            .take(limit)
    }

    override suspend fun count(query: String): Int = events.value.count {
        query.isEmpty() || it.text.contains(query, ignoreCase = true)
    }

    override suspend fun totalBytes(): Long = events.value.sumOf {
        it.text.encodeToByteArray().size.toLong()
    }

    override suspend fun findById(id: String): ClipboardEventEntity? =
        events.value.firstOrNull { it.id == id }

    override suspend fun upsert(entity: ClipboardEventEntity) {
        events.value = events.value.filterNot { it.id == entity.id } + entity
    }

    override suspend fun setPinned(id: String, pinned: Boolean) {
        events.value = events.value.map { if (it.id == id) it.copy(isPinned = pinned) else it }
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        events.value = events.value.map { if (it.id == id) it.copy(isFavorite = favorite) else it }
    }

    override suspend fun newestRows(limit: Int): List<ClipboardEventEntity> = events.value
        .sortedByDescending { it.timestampMillis }
        .take(limit)

    override suspend fun delete(id: String) {
        events.value = events.value.filterNot { it.id == id }
    }

    override suspend fun oldestUnpinnedIds(limit: Int): List<String> = events.value
        .filterNot { it.isPinned }
        .sortedBy { it.timestampMillis }
        .take(limit)
        .map { it.id }

    override suspend fun deleteByIds(ids: List<String>) {
        val removed = ids.toSet()
        events.value = events.value.filterNot { it.id in removed }
    }

    override suspend fun expiredUnpinnedIds(cutoffMillis: Long): List<String> = events.value
        .filterNot { it.isPinned }
        .filter { it.timestampMillis < cutoffMillis }
        .map { it.id }

    private companion object {
        /** Как `ORDER BY isPinned DESC, timestampMillis DESC` в DAO. */
        val ORDER = compareByDescending<ClipboardEventEntity> { it.isPinned }
            .thenByDescending { it.timestampMillis }
    }

    override suspend fun expiredSensitiveIds(nowMillis: Long): List<String> = events.value
        .filterNot { it.isPinned }
        .filter { (it.expiresAtMillis ?: Long.MAX_VALUE) <= nowMillis }
        .map { it.id }
}
