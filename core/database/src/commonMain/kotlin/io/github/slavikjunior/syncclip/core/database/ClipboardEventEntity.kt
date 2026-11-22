package io.github.slavikjunior.syncclip.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "clipboard_events", indices = [Index(value = ["text"])])
data class ClipboardEventEntity(
    @PrimaryKey val id: String,
    val text: String,
    val timestampMillis: Long,
    val sourceDeviceId: String?,
    val isPinned: Boolean,
)
