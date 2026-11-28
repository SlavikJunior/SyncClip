package io.github.slavikjunior.syncclip.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Запись локальной истории.
 *
 * [expiresAtMillis] задаётся, когда включена автоочистка чувствительных данных
 * (US-021): запись живёт до этого времени, если её не закрепили. `null` —
 * обычная запись, удаляется по общим правилам TTL/размера.
 */
@Entity(tableName = "clipboard_events", indices = [Index(value = ["text"])])
data class ClipboardEventEntity(
    @PrimaryKey val id: String,
    val text: String,
    val timestampMillis: Long,
    val sourceDeviceId: String?,
    val isPinned: Boolean,
    val isFavorite: Boolean = false,
    val expiresAtMillis: Long? = null,
)
