package io.github.slavikjunior.syncclip.server.contracts.dto.notification

import kotlinx.serialization.Serializable

@Serializable
data class UnreadNotificationsResponse(
    val items: List<NotificationDto>,
    val nextCursor: String? = null,
)
