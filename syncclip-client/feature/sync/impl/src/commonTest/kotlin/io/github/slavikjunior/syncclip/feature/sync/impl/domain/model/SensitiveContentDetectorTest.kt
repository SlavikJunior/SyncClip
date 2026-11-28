package io.github.slavikjunior.syncclip.feature.sync.impl.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensitiveContentDetectorTest {

    @Test
    fun `password like token is detected`() {
        assertTrue(SensitiveContentDetector.isSensitive("p@ssw0rd123!"))
        assertTrue(SensitiveContentDetector.isSensitive("ghp_x7\$k!9Lm@"))
    }

    @Test
    fun `sentence with a space is not sensitive`() {
        assertFalse(SensitiveContentDetector.isSensitive("Hello, World!"))
    }

    @Test
    fun `long text is not sensitive even without spaces`() {
        val longToken = "a".repeat(49) + "!@"
        assertFalse(SensitiveContentDetector.isSensitive(longToken))
    }

    @Test
    fun `single special character is not enough`() {
        assertFalse(SensitiveContentDetector.isSensitive("hello!"))
    }

    @Test
    fun `ordinary words stay in history`() {
        assertFalse(SensitiveContentDetector.isSensitive("CheckRoomMigrations"))
        assertFalse(SensitiveContentDetector.isSensitive(""))
        assertFalse(SensitiveContentDetector.isSensitive("   "))
    }

    @Test
    fun `ttl matches the five minute promise`() {
        assertTrue(SensitiveContentDetector.SENSITIVE_TTL_MILLIS == 5L * 60L * 1000L)
    }
}
