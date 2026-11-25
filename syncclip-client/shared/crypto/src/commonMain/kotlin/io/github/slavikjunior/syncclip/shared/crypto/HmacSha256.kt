package io.github.slavikjunior.syncclip.shared.crypto

/** HMAC-SHA256 (RFC 2104) — основа PBKDF2. */
internal object HmacSha256 {

    private const val BLOCK_BYTES = 64

    fun compute(key: ByteArray, message: ByteArray): ByteArray {
        val normalized = when {
            key.size > BLOCK_BYTES -> Sha256.hash(key)
            else -> key
        }
        val padded = ByteArray(BLOCK_BYTES)
        normalized.copyInto(padded)

        val innerPad = ByteArray(BLOCK_BYTES) { index ->
            (padded[index].toInt() xor 0x36).toByte()
        }
        val outerPad = ByteArray(BLOCK_BYTES) { index ->
            (padded[index].toInt() xor 0x5c).toByte()
        }

        val inner = Sha256.hash(innerPad + message)
        return Sha256.hash(outerPad + inner)
    }
}
