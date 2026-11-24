package io.github.slavikjunior.syncclip.server.contracts.dto.sync

import kotlinx.serialization.Serializable

/** API_SPEC `SyncPacket`; `timestamp` — client_timestamp в миллисекундах. */
@Serializable
data class SyncPacketDto(
    val eventId: String,
    val originDeviceId: String,
    val encryptedPayload: String,
    val payloadHash: String,
    val timestamp: Long,
)
