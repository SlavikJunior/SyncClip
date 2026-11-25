package io.github.slavikjunior.syncclip.shared.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class AesGcmCipherTest {

    private val zeroKey = ByteArray(32)
    private val zeroIv = ByteArray(12)

    @Test
    fun `nist vector with empty plaintext`() {
        val payload = AesGcm.encrypt(ByteArray(0), zeroKey, zeroIv)

        assertEquals(
            "AAAAAAAAAAAAAAAAUw+K+8dFNrmpY7TxxMtziw==",
            Base64Codec.encode(payload),
        )
        assertEquals(0, AesGcm.decrypt(payload, zeroKey).size)
    }

    @Test
    fun `nist vector with one zero block`() {
        val payload = AesGcm.encrypt(ByteArray(16), zeroKey, zeroIv)

        assertEquals(
            "AAAAAAAAAAAAAAAAzqdAPU1ga24HTsXTuvOdGNDRyKeZmWvwJluYtdSKuRk=",
            Base64Codec.encode(payload),
        )
    }

    @Test
    fun `custom vector matches reference implementation`() {
        val key = ByteArray(32) { index -> ((index * 7 + 1) and 0xFF).toByte() }
        val iv = ByteArray(12) { index -> ((0xA0 + index) and 0xFF).toByte() }
        val text = "SyncClip test привет"

        val payload = AesGcm.encrypt(text.encodeToByteArray(), key, iv)

        assertEquals(
            "oKGio6SlpqeoqaqrAgaJ8+Toct023V5krvRIrlQZbI1G4ckqu9GRAejnewguogxH9FfTPZYN",
            Base64Codec.encode(payload),
        )
        assertEquals(text, AesGcm.decrypt(payload, key).decodeToString())
    }

    @Test
    fun `round trip with random iv`() {
        val cipher = AesGcmPayloadCipher()
        val key = secureRandomBytes(32)
        val text = "Текст с эмодзи 🚀 и переносами\nвторая строка"

        val payload = cipher.encrypt(text, key)

        assertEquals(text, cipher.decrypt(payload, key))
        // Каждый пакет получает свой IV, иначе GCM теряет стойкость.
        assertTrue(cipher.encrypt(text, key) != payload)
    }

    @Test
    fun `tampered tag is rejected`() {
        val cipher = AesGcmPayloadCipher()
        val key = secureRandomBytes(32)
        val payload = Base64Codec.decode(cipher.encrypt("secret", key))
        payload[payload.lastIndex] = (payload.last().toInt() xor 0x01).toByte()

        assertFailsWith<CryptoException> {
            cipher.decrypt(Base64Codec.encode(payload), key)
        }
    }

    @Test
    fun `wrong key is rejected`() {
        val cipher = AesGcmPayloadCipher()
        val payload = cipher.encrypt("secret", secureRandomBytes(32))

        assertFailsWith<CryptoException> {
            cipher.decrypt(payload, secureRandomBytes(32))
        }
    }

    @Test
    fun `payload exceeding the passport limit still round trips`() {
        val cipher = AesGcmPayloadCipher()
        val key = secureRandomBytes(32)
        val text = "a".repeat(512 * 1024)

        val payload = cipher.encrypt(text, key)

        assertEquals(text, cipher.decrypt(payload, key))
    }

    @Test
    fun `base64 round trip keeps bytes`() {
        val bytes = secureRandomBytes(257)

        assertTrue(Base64Codec.decode(Base64Codec.encode(bytes)).contentEquals(bytes))
        assertEquals("", Base64Codec.encode(ByteArray(0)))
    }
}
