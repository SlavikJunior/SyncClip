package io.github.slavikjunior.syncclip.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardEventDao {

    @Query(
        "SELECT * FROM clipboard_events ORDER BY timestampMillis DESC LIMIT :limit OFFSET :offset",
    )
    fun observePage(limit: Int, offset: Int): Flow<List<ClipboardEventEntity>>

    @Query(
        "SELECT * FROM clipboard_events WHERE :query = '' " +
            "OR text LIKE '%' || :query || '%' COLLATE NOCASE " +
            "ORDER BY timestampMillis DESC LIMIT :limit OFFSET :offset",
    )
    fun observeSearchPage(query: String, limit: Int, offset: Int): Flow<List<ClipboardEventEntity>>

    @Query(
        "SELECT COUNT(*) FROM clipboard_events WHERE :query = '' " +
            "OR text LIKE '%' || :query || '%' COLLATE NOCASE",
    )
    suspend fun count(query: String): Int

    @Query("SELECT * FROM clipboard_events WHERE id = :id")
    suspend fun findById(id: String): ClipboardEventEntity?

    @Upsert
    suspend fun upsert(entity: ClipboardEventEntity)

    @Query("UPDATE clipboard_events SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("DELETE FROM clipboard_events WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        "SELECT id FROM clipboard_events WHERE isPinned = 0 ORDER BY timestampMillis ASC LIMIT :limit",
    )
    suspend fun oldestUnpinnedIds(limit: Int): List<String>

    @Query("DELETE FROM clipboard_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query(
        "SELECT id FROM clipboard_events WHERE isPinned = 0 AND timestampMillis < :cutoffMillis",
    )
    suspend fun expiredUnpinnedIds(cutoffMillis: Long): List<String>
}
