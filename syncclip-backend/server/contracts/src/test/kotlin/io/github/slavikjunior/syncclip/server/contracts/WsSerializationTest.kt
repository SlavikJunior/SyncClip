package io.github.slavikjunior.syncclip.server.contracts

import io.github.slavikjunior.syncclip.server.contracts.error.WsErrorCode
import io.github.slavikjunior.syncclip.server.contracts.ws.ClientWsMessage
import io.github.slavikjunior.syncclip.server.contracts.ws.RevocationReason
import io.github.slavikjunior.syncclip.server.contracts.ws.ServerWsMessage
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Примеры из WEBSOCKET_PROTOCOL §3 должны парситься как есть. */
class WsSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }

    @Test
    fun `client sync event example parses`() {
        val text = """
            {
              "type": "SYNC_EVENT",
              "eventId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
              "originDeviceId": "550e8400-e29b-41d4-a716-446655440000",
              "targetDeviceIds": ["7c9e6679-7425-40de-944b-e07fc1f90ae7"],
              "encryptedPayload": "AQIDBAUGBwgJ",
              "payloadHash": "5d41402abc4b2a76b9719d911017c5925d41402abc4b2a76b9719d911017c592",
              "timestamp": 1732112266000
            }
        """.trimIndent()
        val message = json.decodeFromString(ClientWsMessage.serializer(), text)
        val event = message as ClientWsMessage.SyncEvent
        assertEquals("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d", event.eventId)
        assertEquals(listOf("7c9e6679-7425-40de-944b-e07fc1f90ae7"), event.targetDeviceIds)
    }

    @Test
    fun `ack and ping examples parse`() {
        val ack = json.decodeFromString(
            ClientWsMessage.serializer(),
            """{"type":"ACK","eventId":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","deviceId":"7c9e6679-7425-40de-944b-e07fc1f90ae7"}""",
        )
        assertTrue(ack is ClientWsMessage.Ack)

        val ping = json.decodeFromString(ClientWsMessage.serializer(), """{"type":"PING","timestamp":1732112266000}""")
        assertEquals(1732112266000, (ping as ClientWsMessage.Ping).timestamp)
    }

    @Test
    fun `unknown client type fails decoding`() {
        val result = runCatching {
            json.decodeFromString(ClientWsMessage.serializer(), """{"type":"UNKNOWN","x":1}""")
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun `server messages use type discriminator without target ids`() {
        val event = ServerWsMessage.SyncEvent(
            eventId = "e",
            originDeviceId = "o",
            encryptedPayload = "p",
            payloadHash = "h".repeat(64),
            timestamp = 1,
        )
        val encoded = json.encodeToString(ServerWsMessage.serializer(), event)
        assertTrue(encoded.contains("\"type\":\"SYNC_EVENT\""))
        assertTrue(!encoded.contains("targetDeviceIds"))

        val revoked = json.encodeToString(
            ServerWsMessage.serializer(),
            ServerWsMessage.SessionRevoked(RevocationReason.GUEST_SESSION_EXPIRED, "expired"),
        )
        assertTrue(revoked.contains("\"reason\":\"guest_session_expired\""))

        val error = json.encodeToString(
            ServerWsMessage.serializer(),
            ServerWsMessage.Error(WsErrorCode.INVALID_PACKET, "bad", "e"),
        )
        assertTrue(error.contains("\"code\":\"INVALID_PACKET\""))
    }
}
