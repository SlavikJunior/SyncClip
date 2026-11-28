package io.github.slavikjunior.syncclip.feature.sync.impl.data.transport

import io.github.slavikjunior.syncclip.feature.sync.impl.FakeIncomingSink
import io.github.slavikjunior.syncclip.shared.crypto.AesGcmPayloadCipher
import io.github.slavikjunior.syncclip.shared.crypto.PayloadHash
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Общий вход входящих пакетов: ни один битый пакет не должен доходить до
 * транспорта исключением — иначе `receiveLoop` принимает его за обрыв связи.
 */
internal class IncomingPacketProcessorEdgeCasesTest {

    private val key = ByteArray(32) { (it + 1).toByte() }
    private val cipher = AesGcmPayloadCipher { size -> ByteArray(size) { 3 } }
    private val sink = FakeIncomingSink()
    private val processor = IncomingPacketProcessor(cipher, sink)

    private suspend fun applyWithKey(
        payload: String,
        hash: String = PayloadHash.of("text"),
        key: ByteArray = this.key,
        origin: String = "device-b",
    ): Boolean = processor.apply(
        eventId = "event-1",
        originDeviceId = origin,
        encryptedPayload = payload,
        payloadHash = hash,
        timestamp = 1_000,
        key = key,
        selfDeviceId = "device-a",
    )

    @Test
    fun `valid packet is applied`() = runTest {
        val applied = applyWithKey(cipher.encrypt("text", key))

        assertTrue(applied)
        assertEquals(listOf("text"), sink.received.map { it.text })
    }

    @Test
    fun `own packet is never applied`() = runTest {
        val applied = applyWithKey(cipher.encrypt("text", key), origin = "device-a")

        assertFalse(applied)
        assertTrue(sink.received.isEmpty())
    }

    @Test
    fun `empty payload is rejected without throwing`() = runTest {
        assertFalse(applyWithKey(""))
        assertTrue(sink.received.isEmpty())
    }

    @Test
    fun `not base64 payload is rejected without throwing`() = runTest {
        assertFalse(applyWithKey("!!!not-base64!!!"))
    }

    @Test
    fun `truncated payload is rejected without throwing`() = runTest {
        val full = cipher.encrypt("text", key)
        assertFalse(applyWithKey(full.take(full.length / 2)))
    }

    @Test
    fun `wrong key length is rejected without throwing`() = runTest {
        // Повреждённое хранилище ключа: 16 байт вместо 32.
        assertFalse(applyWithKey(cipher.encrypt("text", key), key = ByteArray(16)))
        assertTrue(sink.received.isEmpty())
    }

    @Test
    fun `hash mismatch is rejected without acknowledging`() = runTest {
        assertFalse(applyWithKey(cipher.encrypt("text", key), hash = "0".repeat(64)))
        assertTrue(sink.received.isEmpty())
    }

    @Test
    fun `hash of another text is rejected`() = runTest {
        assertFalse(
            applyWithKey(cipher.encrypt("real", key), hash = PayloadHash.of("expected")),
        )
    }
}
