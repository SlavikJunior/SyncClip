package io.github.slavikjunior.syncclip.feature.sync.impl.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Кодирование/декодирование кадров протокола.
 *
 * Клиентские сообщения сериализуются через полиморфный сериализатор sealed
 * интерфейса: только он добавляет дискриминатор `type`, который ждёт сервер.
 * Серверные кадры разбираются по полю `type` вручную, чтобы неизвестный тип
 * (новая версия сервера) не ронял соединение.
 */
internal object WsCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun encode(message: ClientMessage): String =
        json.encodeToString(ClientMessage.serializer(), message)

    fun decode(text: String): ServerMessage? {
        val body = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: return null
        val type = typeOf(body) ?: return null

        return runCatching {
            when (type) {
                "SYNC_EVENT" -> json.decodeFromJsonElement<ServerMessage.SyncEvent>(body)
                "PONG" -> json.decodeFromJsonElement<ServerMessage.Pong>(body)
                "ACK" -> json.decodeFromJsonElement<ServerMessage.Ack>(body)
                "SESSION_REVOKED" -> json.decodeFromJsonElement<ServerMessage.SessionRevoked>(body)
                "ERROR" -> json.decodeFromJsonElement<ServerMessage.Error>(body)
                else -> null
            }
        }.getOrNull()
    }

    /** Для тестов: JSON-объект кадра как его видит сервер. */
    fun raw(message: ClientMessage): JsonObject =
        json.encodeToJsonElement(ClientMessage.serializer(), message) as JsonObject

    fun typeOf(text: String): String? =
        (runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject)
            ?.let(::typeOf)

    private fun typeOf(body: JsonObject): String? =
        (body["type"] as? JsonPrimitive)?.contentOrNull
}
