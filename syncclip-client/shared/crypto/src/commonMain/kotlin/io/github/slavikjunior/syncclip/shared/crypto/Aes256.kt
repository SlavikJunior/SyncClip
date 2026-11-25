package io.github.slavikjunior.syncclip.shared.crypto

/**
 * AES-256 (FIPS 197) — только шифрование блока.
 *
 * GCM использует AES исключительно в режиме CTR, поэтому расшифровка блока не
 * нужна. Таблица S-box строится из GF(2^8) при инициализации: ручной набор
 * 256 констант — типичный источник опечаток, а генерация проверяется тестами
 * на известных векторах.
 */
internal class Aes256(key: ByteArray) {

    private val roundKeys = IntArray(4 * (ROUNDS + 1))

    init {
        require(key.size == KEY_BYTES) { "AES-256 requires a 32-byte key" }
        expandKey(key)
    }

    fun encryptBlock(input: ByteArray, inputOffset: Int, output: ByteArray, outputOffset: Int) {
        val state = IntArray(16)
        for (column in 0 until 4) {
            for (row in 0 until 4) {
                state[row + 4 * column] = input[inputOffset + row + 4 * column].toInt() and 0xFF
            }
        }

        addRoundKey(state, 0)
        for (round in 1 until ROUNDS) {
            subBytes(state)
            shiftRows(state)
            mixColumns(state)
            addRoundKey(state, round)
        }
        subBytes(state)
        shiftRows(state)
        addRoundKey(state, ROUNDS)

        for (column in 0 until 4) {
            for (row in 0 until 4) {
                output[outputOffset + row + 4 * column] = state[row + 4 * column].toByte()
            }
        }
    }

    private fun expandKey(key: ByteArray) {
        for (word in 0 until NK) {
            roundKeys[word] = readWord(key, word * 4)
        }

        var rcon = 1
        for (word in NK until roundKeys.size) {
            var temp = roundKeys[word - 1]
            when {
                word % NK == 0 -> {
                    temp = subWord(rotateWord(temp)) xor (rcon shl 24)
                    rcon = multiply(rcon, 2)
                }

                word % NK == 4 -> temp = subWord(temp)
            }
            roundKeys[word] = roundKeys[word - NK] xor temp
        }
    }

    private fun addRoundKey(state: IntArray, round: Int) {
        for (column in 0 until 4) {
            val word = roundKeys[round * 4 + column]
            val offset = 4 * column
            state[offset] = state[offset] xor ((word ushr 24) and 0xFF)
            state[offset + 1] = state[offset + 1] xor ((word ushr 16) and 0xFF)
            state[offset + 2] = state[offset + 2] xor ((word ushr 8) and 0xFF)
            state[offset + 3] = state[offset + 3] xor (word and 0xFF)
        }
    }

    private fun subBytes(state: IntArray) {
        for (index in state.indices) {
            state[index] = S_BOX[state[index]]
        }
    }

    private fun shiftRows(state: IntArray) {
        for (row in 1 until 4) {
            val original = IntArray(4) { column -> state[row + 4 * column] }
            for (column in 0 until 4) {
                state[row + 4 * column] = original[(column + row) % 4]
            }
        }
    }

    private fun mixColumns(state: IntArray) {
        for (column in 0 until 4) {
            val offset = 4 * column
            val a0 = state[offset]
            val a1 = state[offset + 1]
            val a2 = state[offset + 2]
            val a3 = state[offset + 3]

            state[offset] = times2(a0) xor (times2(a1) xor a1) xor a2 xor a3
            state[offset + 1] = a0 xor times2(a1) xor (times2(a2) xor a2) xor a3
            state[offset + 2] = a0 xor a1 xor times2(a2) xor (times2(a3) xor a3)
            state[offset + 3] = (times2(a0) xor a0) xor a1 xor a2 xor times2(a3)
        }
    }

    private fun subWord(word: Int): Int =
        (S_BOX[(word ushr 24) and 0xFF] shl 24) or
            (S_BOX[(word ushr 16) and 0xFF] shl 16) or
            (S_BOX[(word ushr 8) and 0xFF] shl 8) or
            S_BOX[word and 0xFF]

    private fun rotateWord(word: Int): Int = (word shl 8) or (word ushr 24)

    private fun readWord(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private companion object {
        const val KEY_BYTES = 32
        const val NK = 8
        const val ROUNDS = 14

        val S_BOX: IntArray = buildSBox()

        fun buildSBox(): IntArray = IntArray(256) { index ->
            val inverse = multiplicativeInverse(index)
            (inverse xor
                rotateLeft8(inverse, 1) xor
                rotateLeft8(inverse, 2) xor
                rotateLeft8(inverse, 3) xor
                rotateLeft8(inverse, 4) xor
                0x63) and 0xFF
        }

        /** Обратный элемент в GF(2^8) через x^254; 0 отображается в 0. */
        fun multiplicativeInverse(value: Int): Int {
            if (value == 0) return 0
            var result = 1
            repeat(254) { result = multiply(result, value) }
            return result
        }

        /** Умножение в GF(2^8) по полиному x^8 + x^4 + x^3 + x + 1. */
        fun multiply(left: Int, right: Int): Int {
            var a = left
            var b = right
            var product = 0
            repeat(8) {
                if (b and 1 != 0) product = product xor a
                val highBit = a and 0x80
                a = (a shl 1) and 0xFF
                if (highBit != 0) a = a xor 0x1B
                b = b shr 1
            }
            return product and 0xFF
        }

        fun rotateLeft8(value: Int, shift: Int): Int =
            ((value shl shift) or (value ushr (8 - shift))) and 0xFF

        fun times2(value: Int): Int =
            ((value shl 1) xor (if (value and 0x80 != 0) 0x1B else 0)) and 0xFF
    }
}
