package io.github.slavikjunior.syncclip.shared.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class JwtExpiryTest {

    // {"exp":2000000000,"sub":"user"}
    private val freshPayload = "eyJleHAiOjIwMDAwMDAwMDAsInN1YiI6InVzZXIifQ"
    // {"exp":1000}
    private val expiredPayload = "eyJleHAiOjEwMDB9"

    private fun token(payload: String) = "eyJhbGciOiJIUzI1NiJ9.$payload.signature"

    @Test
    fun `exp is read from payload`() {
        assertEquals(2_000_000_000_000, JwtExpiry.expiresAtMillis(token(freshPayload)))
        assertEquals(1_000_000, JwtExpiry.expiresAtMillis(token(expiredPayload)))
    }

    @Test
    fun `token expires with skew`() {
        assertTrue(JwtExpiry.isFresh(token(freshPayload), nowMillis = 1_000_000))
        assertFalse(JwtExpiry.isFresh(token(expiredPayload), nowMillis = 1_000_000))
        // За 30 секунд до истечения при запасе 60 секунд токен уже считается старым.
        assertFalse(
            JwtExpiry.isFresh(
                token(expiredPayload),
                nowMillis = 1_000_000 - 30_000,
                skewMillis = 60_000,
            ),
        )
    }

    @Test
    fun `malformed tokens are not fresh`() {
        assertFalse(JwtExpiry.isFresh(null, nowMillis = 0))
        assertFalse(JwtExpiry.isFresh("", nowMillis = 0))
        assertFalse(JwtExpiry.isFresh("not-a-jwt", nowMillis = 0))
        assertFalse(JwtExpiry.isFresh("a.%%%.c", nowMillis = 0))
    }

    /**
     * `exp`, умноженный на 1000, не должен переполняться: иначе токен навсегда
     * «протухает» (отрицательное время) и обновляется на каждом запросе.
     */
    @Test
    fun `absurd exp is rejected instead of overflowing`() {
        // {"exp":9223372036854776}
        val absurd = "eyJleHAiOjkyMjMzNzIwMzY4NTQ3NzZ9"

        assertEquals(null, JwtExpiry.expiresAtMillis(token(absurd)))
        assertFalse(JwtExpiry.isFresh(token(absurd), nowMillis = 1_000_000))
    }
}
