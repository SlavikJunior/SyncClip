package io.github.slavikjunior.syncclip.server.contracts.ws

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/** client→server сообщения (WEBSOCKET_PROTOCOL §3.1–3.4). */
@Serializable
@JsonClassDiscriminator("type")
sealed interface ClientWsMessage {

    @Serializable
    @SerialName("SYNC_EVENT")
    data class SyncEvent(
        val eventId: String,
        val originDeviceId: String,
        val targetDeviceIds: List<String> = emptyList(),
        val encryptedPayload: String,
        val payloadHash: String,
        val timestamp: Long,
    ) : ClientWsMessage

    @Serializable
    @SerialName("ACK")
    data class Ack(
        val eventId: String,
        val deviceId: String,
    ) : ClientWsMessage

    @Serializable
    @SerialName("PING")
    data class Ping(val timestamp: Long) : ClientWsMessage
}
