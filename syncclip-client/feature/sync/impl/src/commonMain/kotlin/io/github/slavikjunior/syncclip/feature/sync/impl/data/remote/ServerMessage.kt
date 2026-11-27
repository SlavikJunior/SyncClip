package io.github.slavikjunior.syncclip.feature.sync.impl.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * server→client сообщения (WEBSOCKET_PROTOCOL §3.2, §3.5–3.7).
 *
 * Неизвестные типы (например, будущие broadcast-сообщения) декодер пропускает:
 * расширение сервера не должно ломать старый клиент.
 */
internal sealed interface ServerMessage {

    @Serializable
    @SerialName("SYNC_EVENT")
    data class SyncEvent(
        val eventId: String,
        val originDeviceId: String,
        val encryptedPayload: String,
        val payloadHash: String,
        val timestamp: Long,
    ) : ServerMessage

    @Serializable
    @SerialName("PONG")
    data class Pong(val timestamp: Long) : ServerMessage

    @Serializable
    @SerialName("SESSION_REVOKED")
    data class SessionRevoked(
        val reason: String,
        val message: String,
    ) : ServerMessage

    @Serializable
    @SerialName("ACK")
    data class Ack(
        val eventId: String,
        val deviceId: String? = null,
    ) : ServerMessage

    @Serializable
    @SerialName("ERROR")
    data class Error(
        val code: String,
        val message: String,
        val eventId: String? = null,
    ) : ServerMessage
}
