package io.github.slavikjunior.syncclip.feature.history.impl.domain.model

/**
 * Доменная модель записи истории — отдельный тип от Entity и DTO (INV-07).
 *
 * [expiresAtMillis] — срок автоудаления чувствительной записи (US-021);
 * `null` — обычная запись, живёт по общим правилам TTL.
 */
internal data class HistoryEntry(
    val id: String,
    val text: String,
    val timestampMillis: Long,
    val sourceDeviceId: String?,
    val isPinned: Boolean,
    val isFavorite: Boolean = false,
    val expiresAtMillis: Long? = null,
)
