package io.github.slavikjunior.syncclip.feature.sync.impl.data.transport

import kotlin.test.Test
import kotlin.test.assertEquals

internal class BackoffPolicyTest {

    private val policy = BackoffPolicy()

    @Test
    fun `delay doubles until the cap`() {
        assertEquals(1_000, policy.delayForAttempt(1))
        assertEquals(2_000, policy.delayForAttempt(2))
        assertEquals(4_000, policy.delayForAttempt(3))
        assertEquals(8_000, policy.delayForAttempt(4))
        assertEquals(16_000, policy.delayForAttempt(5))
        assertEquals(30_000, policy.delayForAttempt(6))
        assertEquals(30_000, policy.delayForAttempt(50))
    }

    @Test
    fun `attempt zero does not produce a negative shift`() {
        assertEquals(1_000, policy.delayForAttempt(0))
    }

    @Test
    fun `five attempts suspend the transport`() {
        assertEquals(5, policy.maxAttempts)
    }
}
