package io.github.slavikjunior.syncclip.shared.crypto

/**
 * AES-256-GCM (NIST SP 800-38D) с 96-битным IV.
 *
 * Формат пакета из протокола: `IV (12) || ciphertext || authTag (16)`.
 * AAD не используется: метаданные пакета (eventId, timestamp) серверу нужны
 * открытыми, а их целостность обеспечивает протокол доставки.
 */
internal object AesGcm {

    const val IV_BYTES: Int = 12
    const val TAG_BYTES: Int = 16
    const val MIN_PAYLOAD_BYTES: Int = IV_BYTES + TAG_BYTES

    fun encrypt(plaintext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        require(iv.size == IV_BYTES) { "GCM requires a 12-byte IV" }
        val cipher = Aes256(key)
        val h = hashSubkey(cipher)
        val j0 = initialCounter(iv)

        val ciphertext = applyCtr(cipher, plaintext, j0)
        val tag = authTag(cipher, h, j0, ciphertext)

        val payload = ByteArray(IV_BYTES + ciphertext.size + TAG_BYTES)
        iv.copyInto(payload, 0)
        ciphertext.copyInto(payload, IV_BYTES)
        tag.copyInto(payload, IV_BYTES + ciphertext.size)
        return payload
    }

    fun decrypt(payload: ByteArray, key: ByteArray): ByteArray {
        if (payload.size < MIN_PAYLOAD_BYTES) {
            throw CryptoException("encrypted payload is too short")
        }
        val iv = payload.copyOfRange(0, IV_BYTES)
        val ciphertext = payload.copyOfRange(IV_BYTES, payload.size - TAG_BYTES)
        val tag = payload.copyOfRange(payload.size - TAG_BYTES, payload.size)

        val cipher = Aes256(key)
        val h = hashSubkey(cipher)
        val j0 = initialCounter(iv)
        val expected = authTag(cipher, h, j0, ciphertext)
        if (!constantTimeEquals(expected, tag)) {
            throw CryptoException("authentication tag mismatch")
        }
        return applyCtr(cipher, ciphertext, j0)
    }

    private fun hashSubkey(cipher: Aes256): ByteArray = ByteArray(16).also { h ->
        cipher.encryptBlock(ByteArray(16), 0, h, 0)
    }

    private fun initialCounter(iv: ByteArray): ByteArray {
        val counter = ByteArray(16)
        iv.copyInto(counter)
        counter[15] = 1
        return counter
    }

    private fun applyCtr(cipher: Aes256, input: ByteArray, j0: ByteArray): ByteArray {
        val output = ByteArray(input.size)
        val counter = j0.copyOf()
        val keystream = ByteArray(16)
        var offset = 0
        while (offset < input.size) {
            incrementCounter(counter)
            cipher.encryptBlock(counter, 0, keystream, 0)
            val blockSize = minOf(16, input.size - offset)
            for (index in 0 until blockSize) {
                output[offset + index] = (input[offset + index].toInt() xor keystream[index].toInt()).toByte()
            }
            offset += blockSize
        }
        return output
    }

    private fun authTag(
        cipher: Aes256,
        h: ByteArray,
        j0: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray {
        val hash = GHash.compute(h, ByteArray(0), ciphertext)
        val mask = ByteArray(16)
        cipher.encryptBlock(j0, 0, mask, 0)
        return ByteArray(TAG_BYTES) { index ->
            (hash[index].toInt() xor mask[index].toInt()).toByte()
        }
    }

    /** Инкремент последних 32 бит счётчика (big-endian). */
    private fun incrementCounter(counter: ByteArray) {
        for (index in 15 downTo 12) {
            val value = (counter[index].toInt() and 0xFF) + 1
            counter[index] = value.toByte()
            if (value and 0xFF != 0) return
        }
    }

    private fun constantTimeEquals(left: ByteArray, right: ByteArray): Boolean {
        if (left.size != right.size) return false
        var difference = 0
        for (index in left.indices) {
            difference = difference or (left[index].toInt() xor right[index].toInt())
        }
        return difference == 0
    }
}
