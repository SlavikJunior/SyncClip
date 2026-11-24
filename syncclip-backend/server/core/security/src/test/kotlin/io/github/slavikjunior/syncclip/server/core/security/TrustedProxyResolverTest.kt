package io.github.slavikjunior.syncclip.server.core.security

import kotlin.test.Test
import kotlin.test.assertEquals

/** SEC-05: X-Forwarded-For доверяем только от сокетного адреса из TRUSTED_PROXIES. */
class TrustedProxyResolverTest {

    private val resolver = TrustedProxyResolver(listOf("10.0.0.0/8", "127.0.0.1", "2001:db8::/32"))

    @Test
    fun `forwarded header is ignored for untrusted socket`() {
        assertEquals(
            "198.51.100.9",
            resolver.resolve("198.51.100.9", "203.0.113.7, 10.1.1.1"),
        )
    }

    @Test
    fun `forwarded header is ignored when no proxies configured`() {
        val empty = TrustedProxyResolver(emptyList())
        assertEquals("198.51.100.9", empty.resolve("198.51.100.9", "203.0.113.7"))
    }

    @Test
    fun `rightmost untrusted hop is the client`() {
        assertEquals("203.0.113.7", resolver.resolve("10.1.1.1", "203.0.113.7, 10.2.2.2"))
        // Клиент может добавить левые значения — они не должны победить.
        assertEquals("203.0.113.7", resolver.resolve("10.1.1.1", "1.2.3.4, 203.0.113.7, 10.2.2.2"))
    }

    @Test
    fun `invalid forwarded values fall back to socket address`() {
        assertEquals("10.1.1.1", resolver.resolve("10.1.1.1", "999.999.999.999"))
        assertEquals("10.1.1.1", resolver.resolve("10.1.1.1", "not-an-ip"))
    }

    @Test
    fun `ipv6 trusted proxy is supported`() {
        assertEquals("2001:db8:1:0:0:0:0:5", resolver.resolve("2001:db8:1::1", "2001:db8:1::5"))
    }

    @Test
    fun `non literal socket address is returned as is`() {
        assertEquals("localhost", resolver.resolve("localhost", "203.0.113.7"))
    }
}
