package io.github.slavikjunior.syncclip.server.core.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.github.slavikjunior.syncclip.server.core.config.JwtConfig
import java.time.Instant
import java.util.Date

/** Выпуск access/refresh JWT (HS256, §7.1). */
class JwtIssuer(
    private val config: JwtConfig,
    private val tokens: SecureTokens,
    private val clock: () -> Instant = Instant::now,
) {
    private val algorithm: Algorithm = Algorithm.HMAC256(config.secret)

    fun issueAccess(
        userId: String,
        deviceId: String,
        tokenVersion: Int,
        ttlSeconds: Long = config.accessTtlSeconds,
    ): Pair<String, Long> {
        val issuedAt = clock()
        val expiresAt = issuedAt.plusSeconds(ttlSeconds)
        val token = JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withSubject(userId)
            .withJWTId(tokens.newJti())
            .withClaim(CLAIM_DEVICE, deviceId)
            .withClaim(CLAIM_VERSION, tokenVersion)
            .withClaim(CLAIM_TYPE, TokenType.ACCESS.wire)
            .withIssuedAt(Date.from(issuedAt))
            .withExpiresAt(Date.from(expiresAt))
            .sign(algorithm)
        return token to expiresAt.epochSecond
    }

    fun issueRefresh(userId: String, deviceId: String, tokenVersion: Int): RefreshToken {
        val issuedAt = clock()
        val expiresAt = issuedAt.plusSeconds(config.refreshTtlDays * 24 * 3600)
        val jti = tokens.newJti()
        val token = JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withSubject(userId)
            .withJWTId(jti)
            .withClaim(CLAIM_DEVICE, deviceId)
            .withClaim(CLAIM_VERSION, tokenVersion)
            .withClaim(CLAIM_TYPE, TokenType.REFRESH.wire)
            .withIssuedAt(Date.from(issuedAt))
            .withExpiresAt(Date.from(expiresAt))
            .sign(algorithm)
        return RefreshToken(token = token, jti = jti, expiresAt = expiresAt)
    }

    fun issuePair(userId: String, deviceId: String, tokenVersion: Int): TokenPair {
        val (access, accessExpiresAt) = issueAccess(userId, deviceId, tokenVersion)
        val refresh = issueRefresh(userId, deviceId, tokenVersion)
        return TokenPair(
            accessToken = access,
            refreshToken = refresh.token,
            accessExpiresAt = accessExpiresAt,
            refreshExpiresAt = refresh.expiresAt.epochSecond,
            refreshJti = refresh.jti,
        )
    }

    data class RefreshToken(val token: String, val jti: String, val expiresAt: Instant)

    companion object {
        const val CLAIM_DEVICE = "dev"
        const val CLAIM_VERSION = "ver"
        const val CLAIM_TYPE = "typ"
    }
}
