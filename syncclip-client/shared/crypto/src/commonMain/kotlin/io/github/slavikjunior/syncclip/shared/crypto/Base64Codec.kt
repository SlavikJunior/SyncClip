package io.github.slavikjunior.syncclip.shared.crypto

/**
 * Base64 (RFC 4648, стандартный алфавит с паддингом).
 *
 * Своя реализация вместо `kotlin.io.encoding.Base64`: тот API был
 * экспериментальным, а формат пакета зафиксирован протоколом и должен
 * совпадать с серверным `java.util.Base64` на всех платформах.
 */
internal object Base64Codec {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val PAD = '='

    fun encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val output = StringBuilder((bytes.size + 2) / 3 * 4)
        var index = 0
        while (index < bytes.size) {
            val b0 = bytes[index].toInt() and 0xFF
            val b1 = if (index + 1 < bytes.size) bytes[index + 1].toInt() and 0xFF else -1
            val b2 = if (index + 2 < bytes.size) bytes[index + 2].toInt() and 0xFF else -1

            output.append(ALPHABET[b0 shr 2])
            output.append(ALPHABET[((b0 and 0x03) shl 4) or (if (b1 >= 0) b1 shr 4 else 0)])
            if (b1 >= 0) {
                output.append(ALPHABET[((b1 and 0x0F) shl 2) or (if (b2 >= 0) b2 shr 6 else 0)])
            } else {
                output.append(PAD)
            }
            if (b2 >= 0) {
                output.append(ALPHABET[b2 and 0x3F])
            } else {
                output.append(PAD)
            }
            index += 3
        }
        return output.toString()
    }

    fun decode(text: String): ByteArray {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return ByteArray(0)
        if (cleaned.length % 4 != 0) throw CryptoException("base64: invalid length")

        val padding = cleaned.takeLastWhile { it == PAD }.length
        if (padding > 2) throw CryptoException("base64: invalid padding")
        val output = ByteArray(cleaned.length / 4 * 3 - padding)
        var outIndex = 0

        var index = 0
        while (index < cleaned.length) {
            val c0 = valueOf(cleaned[index])
            val c1 = valueOf(cleaned[index + 1])
            val c2 = charOrPad(cleaned[index + 2])
            val c3 = charOrPad(cleaned[index + 3])

            output[outIndex++] = ((c0 shl 2) or (c1 shr 4)).toByte()
            if (c2 >= 0) {
                if (outIndex < output.size) {
                    output[outIndex++] = (((c1 and 0x0F) shl 4) or (c2 shr 2)).toByte()
                }
            }
            if (c3 >= 0 && outIndex < output.size) {
                output[outIndex++] = (((c2 and 0x03) shl 6) or c3).toByte()
            }
            index += 4
        }
        return output
    }

    private fun charOrPad(char: Char): Int = if (char == PAD) -1 else valueOf(char)

    private fun valueOf(char: Char): Int {
        val index = ALPHABET.indexOf(char)
        if (index < 0) throw CryptoException("base64: invalid character")
        return index
    }
}
