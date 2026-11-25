package io.github.slavikjunior.syncclip.shared.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

internal class Sha256Test {

    @Test
    fun `known vectors`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hexOf(""),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hexOf("abc"),
        )
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hexOf("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }

    @Test
    fun `multi block message is hashed`() {
        // Длиннее одного блока (64 байта): проверяет padding и перенос длины.
        val text = "a".repeat(1000)
        val hex = Sha256.hexOf(text)

        assertEquals(64, hex.length)
        assertEquals(hex, Sha256.hexOf(text))
    }
}
