package io.github.slavikjunior.syncclip.shared.crypto

/**
 * Шифрование содержимого буфера.
 *
 * Base64-строка — ровно то, что уходит в `encryptedPayload` протокола.
 */
public interface PayloadCipher {

    /** @param key 32-байтный ключ E2EE. @return base64(IV || ciphertext || tag). */
    public fun encrypt(plaintext: String, key: ByteArray): String

    /** @throws CryptoException если ключ неверный или пакет повреждён. */
    public fun decrypt(payload: String, key: ByteArray): String
}
