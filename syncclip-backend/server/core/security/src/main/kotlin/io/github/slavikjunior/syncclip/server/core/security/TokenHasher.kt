package io.github.slavikjunior.syncclip.server.core.security

import java.security.MessageDigest

/** SHA-256 hex: jti и invite-коды хранятся только в этом виде (§7.2). */
object TokenHasher {
    fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .toHex()

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (byte in this@toHex) {
            append(HEX[(byte.toInt() shr 4) and 0x0F])
            append(HEX[byte.toInt() and 0x0F])
        }
    }

    private const val HEX = "0123456789abcdef"
}
