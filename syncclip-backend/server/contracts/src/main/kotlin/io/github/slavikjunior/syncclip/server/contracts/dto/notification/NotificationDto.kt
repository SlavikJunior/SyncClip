package io.github.slavikjunior.syncclip.server.contracts.dto.notification

import kotlinx.serialization.Serializable

/** Элемент производной ленты (CG-04): неудачная попытка входа/мастер-пароля. */
@Serializable
data class NotificationDto(
    val id: String,
    val type: String,
    val createdAt: String,
    val ip: String? = null,
    /** Модель устройства из `X-Device-Model` (не User-Agent) — LOW-08. */
    val deviceModel: String? = null,
    /** User-Agent запроса; добавлено аддитивно для честной семантики. */
    val userAgent: String? = null,
)
