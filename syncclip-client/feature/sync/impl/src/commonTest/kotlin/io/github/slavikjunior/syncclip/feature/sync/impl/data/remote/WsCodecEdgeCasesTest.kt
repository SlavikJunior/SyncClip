package io.github.slavikjunior.syncclip.feature.sync.impl.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Разбор кадров сервера: неизвестная версия, битый JSON, неверные типы полей.
 *
 * Старый клиент обязан пережить новое сообщение сервера и не закрыть сокет:
 * иначе одно расширение протокола выключило бы синхронизацию у всех.
 */
internal class WsCodecEdgeCasesTest {

    @Test
    fun `unknown message type is skipped`() {
        assertNull(WsCodec.decode("""{"type":"FUTURE_FEATURE","payload":{}}"""))
    }

    @Test
    fun `missing type is skipped`() {
        assertNull(WsCodec.decode("""{"eventId":"1"}"""))
    }

    @Test
    fun `non object payload is skipped`() {
        assertNull(WsCodec.decode("[]"))
        assertNull(WsCodec.decode("\"SYNC_EVENT\""))
        assertNull(WsCodec.decode("42"))
        assertNull(WsCodec.decode("null"))
        assertNull(WsCodec.decode(""))
    }

    @Test
    fun `broken json is skipped`() {
        assertNull(WsCodec.decode("""{"type":"SYNC_EVENT",""".trimIndent()))
        assertNull(WsCodec.decode("{"))
        assertNull(WsCodec.decode("\u0000\u0001"))
    }

    @Test
    fun `numeric type is not coerced into a message`() {
        assertNull(WsCodec.decode("""{"type":42}"""))
    }

    @Test
    fun `known type with wrong field types is skipped`() {
        assertNull(
            WsCodec.decode(
                """{"type":"SYNC_EVENT","eventId":1,"originDeviceId":"b",
                   "encryptedPayload":"x","payloadHash":"h","timestamp":"now"}""",
            ),
        )
    }

    @Test
    fun `known type with missing required field is skipped`() {
        assertNull(WsCodec.decode("""{"type":"SYNC_EVENT","eventId":"1"}"""))
    }

    @Test
    fun `extra fields are tolerated`() {
        val message = WsCodec.decode(
            """{"type":"ACK","eventId":"1","deviceId":"d","futureField":{"a":[1,2]}}""",
        )

        assertTrue(message is ServerMessage.Ack)
        assertEquals("1", (message as ServerMessage.Ack).eventId)
    }

    @Test
    fun `deeply nested json does not break the decoder`() {
        val deep = "{".repeat(200) + "}".repeat(200)

        assertNull(WsCodec.decode("""{"type":"SYNC_EVENT","eventId":$deep}"""))
    }

    @Test
    fun `client frames carry the protocol discriminator`() {
        val frame = WsCodec.raw(
            ClientMessage.Ack(eventId = "event-1", deviceId = "device-a"),
        ).jsonObject

        assertEquals("ACK", frame["type"]?.jsonPrimitive?.content)
        assertEquals("event-1", frame["eventId"]?.jsonPrimitive?.content)
        assertEquals("device-a", frame["deviceId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `ping is encoded with a timestamp`() {
        val frame = Json.parseToJsonElement(WsCodec.encode(ClientMessage.Ping(123))).jsonObject

        assertEquals("PING", frame["type"]?.jsonPrimitive?.content)
        assertEquals("123", frame["timestamp"]?.jsonPrimitive?.content)
    }
}
