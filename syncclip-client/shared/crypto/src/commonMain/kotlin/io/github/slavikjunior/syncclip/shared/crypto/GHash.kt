package io.github.slavikjunior.syncclip.shared.crypto

/**
 * GHASH (NIST SP 800-38D): умножение в GF(2^128) на H.
 *
 * Реализация битовая, без таблиц: 128 итераций на блок. Для лимита паспорта
 * (512 КиБ) это ~4 млн операций — заметно меньше, чем время сети, зато нет
 * таблиц, зависящих от ключа, и проще доказать корректность тестами.
 */
internal object GHash {

    private const val BLOCK_BYTES = 16

    /** R = 11100001 || 0^120 из спецификации GCM. */
    private const val R_HIGH: ULong = 0xE100000000000000uL
    private const val R_LOW: ULong = 0uL

    fun compute(h: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
        require(h.size == BLOCK_BYTES)
        val hHigh = readHigh(h)
        val hLow = readLow(h)

        var stateHigh = 0uL
        var stateLow = 0uL

        var offset = 0
        while (offset < aad.size) {
            val block = ByteArray(BLOCK_BYTES)
            val size = minOf(BLOCK_BYTES, aad.size - offset)
            aad.copyInto(block, 0, offset, offset + size)
            stateHigh = stateHigh xor readHigh(block)
            stateLow = stateLow xor readLow(block)
            val product = multiply(stateHigh, stateLow, hHigh, hLow)
            stateHigh = product.first
            stateLow = product.second
            offset += BLOCK_BYTES
        }

        offset = 0
        while (offset < ciphertext.size) {
            val block = ByteArray(BLOCK_BYTES)
            val size = minOf(BLOCK_BYTES, ciphertext.size - offset)
            ciphertext.copyInto(block, 0, offset, offset + size)
            stateHigh = stateHigh xor readHigh(block)
            stateLow = stateLow xor readLow(block)
            val product = multiply(stateHigh, stateLow, hHigh, hLow)
            stateHigh = product.first
            stateLow = product.second
            offset += BLOCK_BYTES
        }

        val lengths = ByteArray(BLOCK_BYTES)
        writeLong(aad.size.toLong() * 8, lengths, 0)
        writeLong(ciphertext.size.toLong() * 8, lengths, 8)
        stateHigh = stateHigh xor readHigh(lengths)
        stateLow = stateLow xor readLow(lengths)
        val product = multiply(stateHigh, stateLow, hHigh, hLow)

        val result = ByteArray(BLOCK_BYTES)
        writeLong(product.first.toLong(), result, 0)
        writeLong(product.second.toLong(), result, 8)
        return result
    }

    private fun multiply(
        xHigh: ULong,
        xLow: ULong,
        hHigh: ULong,
        hLow: ULong,
    ): Pair<ULong, ULong> {
        var zHigh = 0uL
        var zLow = 0uL
        var vHigh = hHigh
        var vLow = hLow

        for (bit in 0 until 128) {
            val set = if (bit < 64) {
                (xHigh shr (63 - bit)) and 1uL
            } else {
                (xLow shr (127 - bit)) and 1uL
            }
            if (set == 1uL) {
                zHigh = zHigh xor vHigh
                zLow = zLow xor vLow
            }

            val lowestBit = vLow and 1uL
            vLow = (vLow shr 1) or (vHigh shl 63)
            vHigh = vHigh shr 1
            if (lowestBit == 1uL) {
                vHigh = vHigh xor R_HIGH
                vLow = vLow xor R_LOW
            }
        }
        return zHigh to zLow
    }

    private fun readHigh(bytes: ByteArray): ULong = readLong(bytes, 0).toULong()

    private fun readLow(bytes: ByteArray): ULong = readLong(bytes, 8).toULong()

    private fun readLong(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        for (index in 0 until 8) {
            value = (value shl 8) or (bytes[offset + index].toLong() and 0xFF)
        }
        return value
    }

    private fun writeLong(value: Long, bytes: ByteArray, offset: Int) {
        for (index in 0 until 8) {
            bytes[offset + index] = (value ushr (56 - index * 8)).toByte()
        }
    }
}
