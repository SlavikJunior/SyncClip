package io.github.slavikjunior.syncclip.feature.sync.impl.data.remote

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * client→server сообщения (WEBSOCKET_PROTOCOL §3.1–3.4).
 *
 * Поля и имена совпадают с контрактом сервера
 * (`server/contracts/.../ws/ClientWsMessage.kt`): клиент и сервер — разные
 * Gradle-проекты, поэтому DTO дублируется, а совпадение проверяется тестами.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
internal sealed interface ClientMessage {

    @Serializable
    @SerialName("SYNC_EVENT")
    data class SyncEvent(
        val eventId: String,
        val originDeviceId: String,
        val targetDeviceIds: List<String> = emptyList(),
        val encryptedPayload: String,
        val payloadHash: String,
        val timestamp: Long,
    ) : ClientMessage

    @Serializable
    @SerialName("ACK")
    data class Ack(
        val eventId: String,
        val deviceId: String,
    ) : ClientMessage

    @Serializable
    @SerialName("PING")
    data class Ping(val timestamp: Long) : ClientMessage
}
