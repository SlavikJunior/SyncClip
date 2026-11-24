package io.github.slavikjunior.syncclip.server.contracts.ws

import io.github.slavikjunior.syncclip.server.contracts.dto.device.DeviceDto
import io.github.slavikjunior.syncclip.server.contracts.error.WsErrorCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/** server→client сообщения; DEVICE_UPDATED/DEVICE_REMOVED — CG-03. */
@Serializable
@JsonClassDiscriminator("type")
sealed interface ServerWsMessage {

    @Serializable
    @SerialName("SYNC_EVENT")
    data class SyncEvent(
        val eventId: String,
        val originDeviceId: String,
        val encryptedPayload: String,
        val payloadHash: String,
        val timestamp: Long,
    ) : ServerWsMessage

    @Serializable
    @SerialName("PONG")
    data class Pong(val timestamp: Long) : ServerWsMessage

    @Serializable
    @SerialName("SESSION_REVOKED")
    data class SessionRevoked(
        val reason: RevocationReason,
        val message: String,
    ) : ServerWsMessage

    @Serializable
    @SerialName("ERROR")
    data class Error(
        val code: WsErrorCode,
        val message: String,
        val eventId: String? = null,
    ) : ServerWsMessage

    @Serializable
    @SerialName("DEVICE_UPDATED")
    data class DeviceUpdated(val device: DeviceDto) : ServerWsMessage

    @Serializable
    @SerialName("DEVICE_REMOVED")
    data class DeviceRemoved(val deviceId: String) : ServerWsMessage
}
