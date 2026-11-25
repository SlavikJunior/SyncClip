package io.github.slavikjunior.syncclip.feature.history.impl.domain.model

/** Доменная модель записи истории — отдельный тип от Entity и DTO (INV-07). */
internal data class HistoryEntry(
    val id: String,
    val text: String,
    val timestampMillis: Long,
    val sourceDeviceId: String?,
    val isPinned: Boolean,
)
