package io.github.slavikjunior.syncclip.shared.crypto

/**
 * PBKDF2-HMAC-SHA256 (RFC 8018).
 *
 * Мастер-пароль превращается в 256-битный ключ E2EE. Соль — `userId`:
 * одинаковый пароль на разных аккаунтах даёт разные ключи, а сервер не знает
 * ни пароль, ни ключ.
 */
public object Pbkdf2 {

    public const val DEFAULT_ITERATIONS: Int = 100_000
    public const val KEY_BYTES: Int = 32

    public fun deriveKey(
        password: String,
        salt: String,
        iterations: Int = DEFAULT_ITERATIONS,
        lengthBytes: Int = KEY_BYTES,
    ): ByteArray = derive(
        password = password.encodeToByteArray(),
        salt = salt.encodeToByteArray(),
        iterations = iterations,
        lengthBytes = lengthBytes,
    )

    public fun derive(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        lengthBytes: Int = KEY_BYTES,
    ): ByteArray {
        require(iterations > 0) { "iterations must be positive" }
        require(lengthBytes > 0) { "lengthBytes must be positive" }

        val blocks = (lengthBytes + Sha256.DIGEST_BYTES - 1) / Sha256.DIGEST_BYTES
        val derived = ByteArray(blocks * Sha256.DIGEST_BYTES)

        for (block in 1..blocks) {
            val blockIndex = byteArrayOf(
                (block ushr 24).toByte(),
                (block ushr 16).toByte(),
                (block ushr 8).toByte(),
                block.toByte(),
            )
            var u = HmacSha256.compute(password, salt + blockIndex)
            val accumulator = u.copyOf()

            for (iteration in 1 until iterations) {
                u = HmacSha256.compute(password, u)
                for (index in accumulator.indices) {
                    accumulator[index] = (accumulator[index].toInt() xor u[index].toInt()).toByte()
                }
            }
            accumulator.copyInto(derived, (block - 1) * Sha256.DIGEST_BYTES)
        }

        return derived.copyOf(lengthBytes)
    }
}
