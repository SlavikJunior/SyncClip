package io.github.slavikjunior.syncclip.shared.crypto

/**
 * AES-256-GCM с уникальным случайным IV на каждый пакет.
 *
 * [ivSource] подменяется в тестах: детерминированный IV нужен, чтобы сверять
 * байты с `javax.crypto` и известными векторами.
 */
public class AesGcmPayloadCipher(
    private val ivSource: (Int) -> ByteArray = { size -> secureRandomBytes(size) },
) : PayloadCipher {

    override fun encrypt(plaintext: String, key: ByteArray): String {
        requireKey(key)
        val iv = ivSource(AesGcm.IV_BYTES)
        require(iv.size == AesGcm.IV_BYTES) { "IV source returned ${iv.size} bytes" }
        val payload = AesGcm.encrypt(plaintext.encodeToByteArray(), key, iv)
        return Base64Codec.encode(payload)
    }

    override fun decrypt(payload: String, key: ByteArray): String {
        requireKey(key)
        val bytes = try {
            Base64Codec.decode(payload)
        } catch (error: CryptoException) {
            throw error
        }
        return AesGcm.decrypt(bytes, key).decodeToString()
    }

    private fun requireKey(key: ByteArray) {
        require(key.size == KEY_BYTES) { "E2EE key must be $KEY_BYTES bytes" }
    }

    private companion object {
        const val KEY_BYTES = 32
    }
}
