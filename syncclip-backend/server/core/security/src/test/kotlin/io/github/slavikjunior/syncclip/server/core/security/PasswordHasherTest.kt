package io.github.slavikjunior.syncclip.server.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordHasherTest {

    private val hasher = Pbkdf2PasswordHasher(iterations = 1_000)

    @Test
    fun `hash verifies with the same password`() {
        val encoded = hasher.hash("SecurePassword123!")
        assertTrue(hasher.verify("SecurePassword123!", encoded))
        assertEquals("pbkdf2-sha256", hasher.algorithm)
    }

    @Test
    fun `wrong password is rejected`() {
        val encoded = hasher.hash("SecurePassword123!")
        assertFalse(hasher.verify("SecurePassword124!", encoded))
    }

    @Test
    fun `salt is random per hash`() {
        val first = hasher.hash("same-password")
        val second = hasher.hash("same-password")
        assertNotEquals(first, second)
        assertTrue(hasher.verify("same-password", first))
        assertTrue(hasher.verify("same-password", second))
    }

    @Test
    fun `malformed hash is rejected without exception`() {
        assertFalse(hasher.verify("x", "garbage"))
        assertFalse(hasher.verify("x", "pbkdf2-sha256\$abc\$s\$h"))
        assertFalse(hasher.verify("x", "argon2id\$1\$2\$3"))
    }

    @Test
    fun `iterations are taken from the encoded value`() {
        val encoded = Pbkdf2PasswordHasher(iterations = 2_000).hash("password-123")
        assertTrue(hasher.verify("password-123", encoded))
    }

    @Test
    fun `needs rehash when iterations or algorithm differ`() {
        assertFalse(hasher.needsRehash(hasher.hash("password-123")))
        val legacy = Pbkdf2PasswordHasher(iterations = 1_000).hash("password-123")
        assertTrue(Pbkdf2PasswordHasher(iterations = 2_000).needsRehash(legacy))
        assertTrue(hasher.needsRehash("argon2id\$1\$2\$3"))
        assertTrue(hasher.needsRehash("garbage"))
    }
}
