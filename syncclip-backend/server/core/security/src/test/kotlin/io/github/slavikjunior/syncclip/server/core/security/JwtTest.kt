package io.github.slavikjunior.syncclip.server.core.security

import io.github.slavikjunior.syncclip.server.core.config.JwtConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JwtTest {

    private val config = JwtConfig(
        issuer = "syncclip",
        audience = "syncclip-client",
        secret = "0123456789abcdef0123456789abcdef",
        accessTtlSeconds = 3600,
        refreshTtlDays = 30,
        clockSkewSeconds = 0,
    )
    private val issuer = JwtIssuer(config, SecureTokens())
    private val verifier = JwtVerifier(config)

    @Test
    fun `access token round trip keeps claims`() {
        val (token, _) = issuer.issueAccess("user-1", "device-1", tokenVersion = 4)
        val claims = verifier.verifyAccess(token)
        assertEquals("user-1", claims.subject)
        assertEquals("device-1", claims.deviceId)
        assertEquals(4, claims.tokenVersion)
        assertEquals(TokenType.ACCESS, claims.type)
        assertEquals("syncclip", claims.issuer)
    }

    @Test
    fun `refresh token is rejected as access`() {
        val pair = issuer.issuePair("user-1", "device-1", tokenVersion = 0)
        assertFailsWith<TokenException.WrongType> { verifier.verifyAccess(pair.refreshToken) }
    }

    @Test
    fun `expired token is reported as expired`() {
        val (token, _) = issuer.issueAccess("user-1", "device-1", tokenVersion = 0, ttlSeconds = -60)
        assertFailsWith<TokenException.Expired> { verifier.verifyAccess(token) }
    }

    @Test
    fun `foreign secret is rejected`() {
        val foreign = JwtIssuer(config.copy(secret = "ffffffffffffffffffffffffffffffff"), SecureTokens())
        val (token, _) = foreign.issueAccess("user-1", "device-1", tokenVersion = 0)
        assertFailsWith<TokenException.Invalid> { verifier.verifyAccess(token) }
    }

    @Test
    fun `token without exp claim is rejected`() {
        val token = com.auth0.jwt.JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withSubject("user-1")
            .withJWTId("jti-without-exp")
            .withClaim(JwtIssuer.CLAIM_DEVICE, "device-1")
            .withClaim(JwtIssuer.CLAIM_VERSION, 0)
            .withClaim(JwtIssuer.CLAIM_TYPE, TokenType.ACCESS.wire)
            .withIssuedAt(java.util.Date())
            .sign(com.auth0.jwt.algorithms.Algorithm.HMAC256(config.secret))
        assertFailsWith<TokenException.Invalid> { verifier.verifyAccess(token) }
    }

    @Test
    fun `refresh jti is unique`() {
        val first = issuer.issueRefresh("user-1", "device-1", 0)
        val second = issuer.issueRefresh("user-1", "device-1", 0)
        assertTrue(first.jti != second.jti)
        assertEquals(64, TokenHasher.sha256Hex(first.jti).length)
    }
}
