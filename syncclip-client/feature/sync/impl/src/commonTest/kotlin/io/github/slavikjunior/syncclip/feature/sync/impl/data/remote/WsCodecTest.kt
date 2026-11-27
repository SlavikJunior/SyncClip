package io.github.slavikjunior.syncclip.feature.sync.impl.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

internal class WsCodecTest {

    @Test
    fun `sync event carries the discriminator and protocol fields`() {
        val encoded = WsCodec.encode(
            ClientMessage.SyncEvent(
                eventId = "event-1",
                originDeviceId = "device-a",
                targetDeviceIds = listOf("device-b"),
                encryptedPayload = "ciphertext",
                payloadHash = "a".repeat(64),
                timestamp = 1_700_000_000_000,
            ),
        )

        val body = WsCodec.raw(
            ClientMessage.SyncEvent(
                eventId = "event-1",
                originDeviceId = "device-a",
                targetDeviceIds = listOf("device-b"),
                encryptedPayload = "ciphertext",
                payloadHash = "a".repeat(64),
                timestamp = 1_700_000_000_000,
            ),
        )

        assertEquals("SYNC_EVENT", body["type"].toString().trim('"'))
        assertEquals("event-1", body["eventId"].toString().trim('"'))
        assertEquals("device-a", body["originDeviceId"].toString().trim('"'))
        assertEquals("[\"device-b\"]", body["targetDeviceIds"].toString())
        assertEquals("ciphertext", body["encryptedPayload"].toString().trim('"'))
        assertEquals(1_700_000_000_000L, body["timestamp"].toString().toLong())
        assertEquals("SYNC_EVENT", WsCodec.typeOf(encoded))
    }

    @Test
    fun `ack and ping have their own types`() {
        assertEquals(
            "ACK",
            WsCodec.typeOf(WsCodec.encode(ClientMessage.Ack("event-1", "device-a"))),
        )
        assertEquals("PING", WsCodec.typeOf(WsCodec.encode(ClientMessage.Ping(1))))
    }

    @Test
    fun `server frames are decoded`() {
        val event = WsCodec.decode(
            """{"type":"SYNC_EVENT","eventId":"e","originDeviceId":"d",
               "encryptedPayload":"p","payloadHash":"h","timestamp":5}""",
        )
        val parsed = assertIs<ServerMessage.SyncEvent>(event)
        assertEquals("e", parsed.eventId)
        assertEquals(5, parsed.timestamp)

        assertIs<ServerMessage.Pong>(WsCodec.decode("""{"type":"PONG","timestamp":7}"""))
        assertIs<ServerMessage.Error>(
            WsCodec.decode("""{"type":"ERROR","code":"RATE_LIMIT_EXCEEDED","message":"slow"}"""),
        )
        assertIs<ServerMessage.SessionRevoked>(
            WsCodec.decode("""{"type":"SESSION_REVOKED","reason":"device_removed","message":"x"}"""),
        )
    }

    @Test
    fun `unknown or broken frames are skipped`() {
        assertNull(WsCodec.decode("""{"type":"DEVICE_UPDATED","device":{}}"""))
        assertNull(WsCodec.decode("""{"type":"FUTURE_THING"}"""))
        assertNull(WsCodec.decode("not json"))
        assertNull(WsCodec.decode("[]"))
    }
}
