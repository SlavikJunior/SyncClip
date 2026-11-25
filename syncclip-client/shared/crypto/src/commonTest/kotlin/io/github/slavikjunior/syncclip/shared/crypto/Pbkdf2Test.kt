package io.github.slavikjunior.syncclip.shared.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

internal class Pbkdf2Test {

    @Test
    fun `rfc vectors for hmac-sha256`() {
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            Sha256.hex(Pbkdf2.deriveKey("password", "salt", iterations = 1)),
        )
        assertEquals(
            "ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43",
            Sha256.hex(Pbkdf2.deriveKey("password", "salt", iterations = 2)),
        )
        assertEquals(
            "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
            Sha256.hex(Pbkdf2.deriveKey("password", "salt", iterations = 4096)),
        )
    }

    @Test
    fun `salt makes keys different`() {
        val first = Pbkdf2.deriveKey("master", "user-a", iterations = 10)
        val second = Pbkdf2.deriveKey("master", "user-b", iterations = 10)

        assertNotEquals(Sha256.hex(first), Sha256.hex(second))
        assertEquals(Pbkdf2.KEY_BYTES, first.size)
    }

    @Test
    fun `same input is deterministic`() {
        assertEquals(
            Sha256.hex(Pbkdf2.deriveKey("master", "user-a", iterations = 10)),
            Sha256.hex(Pbkdf2.deriveKey("master", "user-a", iterations = 10)),
        )
    }
}
