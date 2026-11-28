package io.github.slavikjunior.syncclip.feature.history.impl.data.mapper

import io.github.slavikjunior.syncclip.core.database.ClipboardEventEntity
import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntry

internal fun ClipboardEventEntity.toDomain(): HistoryEntry = HistoryEntry(
    id = id,
    text = text,
    timestampMillis = timestampMillis,
    sourceDeviceId = sourceDeviceId,
    isPinned = isPinned,
    isFavorite = isFavorite,
    expiresAtMillis = expiresAtMillis,
)

internal fun HistoryEntry.toEntity(): ClipboardEventEntity = ClipboardEventEntity(
    id = id,
    text = text,
    timestampMillis = timestampMillis,
    sourceDeviceId = sourceDeviceId,
    isPinned = isPinned,
    isFavorite = isFavorite,
    expiresAtMillis = expiresAtMillis,
)
