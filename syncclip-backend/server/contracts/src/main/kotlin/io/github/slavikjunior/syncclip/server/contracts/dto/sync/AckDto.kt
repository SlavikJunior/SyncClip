package io.github.slavikjunior.syncclip.server.contracts.dto.sync

import kotlinx.serialization.Serializable

@Serializable
data class AckDto(
    val eventId: String,
    val deviceId: String,
)
