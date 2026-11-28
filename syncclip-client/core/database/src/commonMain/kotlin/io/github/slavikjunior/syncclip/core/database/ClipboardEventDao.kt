package io.github.slavikjunior.syncclip.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardEventDao {

    @Query(
        "SELECT * FROM clipboard_events ORDER BY isPinned DESC, timestampMillis DESC " +
            "LIMIT :limit OFFSET :offset",
    )
    fun observePage(limit: Int, offset: Int): Flow<List<ClipboardEventEntity>>

    @Query(
        "SELECT * FROM clipboard_events WHERE :query = '' " +
            "OR text LIKE '%' || :query || '%' COLLATE NOCASE " +
            "ORDER BY isPinned DESC, timestampMillis DESC LIMIT :limit OFFSET :offset",
    )
    fun observeSearchPage(query: String, limit: Int, offset: Int): Flow<List<ClipboardEventEntity>>

    @Query(
        "SELECT COUNT(*) FROM clipboard_events WHERE :query = '' " +
            "OR text LIKE '%' || :query || '%' COLLATE NOCASE",
    )
    suspend fun count(query: String): Int

    /** Последние [limit] записей для экспорта (US-018): без Flow, один снимок. */
    @Query("SELECT * FROM clipboard_events ORDER BY timestampMillis DESC LIMIT :limit")
    suspend fun newestRows(limit: Int): List<ClipboardEventEntity>

    /** Суммарный объём текста в байтах UTF-8 — для лимита памяти FR-4.3. */
    @Query("SELECT COALESCE(SUM(LENGTH(CAST(text AS BLOB))), 0) FROM clipboard_events")
    suspend fun totalBytes(): Long

    @Query("SELECT * FROM clipboard_events WHERE id = :id")
    suspend fun findById(id: String): ClipboardEventEntity?

    @Upsert
    suspend fun upsert(entity: ClipboardEventEntity)

    @Query("UPDATE clipboard_events SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE clipboard_events SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

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

    /**
     * Записи с истёкшим сроком автоудаления (US-021). Закреплённые не удаляются:
     * пользователь явно сохранил их.
     */
    @Query(
        "SELECT id FROM clipboard_events " +
            "WHERE isPinned = 0 AND expiresAtMillis IS NOT NULL AND expiresAtMillis <= :nowMillis",
    )
    suspend fun expiredSensitiveIds(nowMillis: Long): List<String>
}
