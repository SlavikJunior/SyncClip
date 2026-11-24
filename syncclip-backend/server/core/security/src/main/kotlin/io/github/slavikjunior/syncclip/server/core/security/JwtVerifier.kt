package io.github.slavikjunior.syncclip.server.core.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.exceptions.TokenExpiredException
import com.auth0.jwt.interfaces.DecodedJWT
import io.github.slavikjunior.syncclip.server.core.config.JwtConfig

/** Проверка подписи/exp/iss/aud и типа токена (дизайн §7.1, шаги 1–2). */
class JwtVerifier(config: JwtConfig) {

    val delegate: JWTVerifier = JWT.require(Algorithm.HMAC256(config.secret))
        .withIssuer(config.issuer)
        .withAudience(config.audience)
        // Подписанный токен без exp иначе жил бы вечно (LOW-02).
        .withClaimPresence("exp")
        .acceptLeeway(config.clockSkewSeconds)
        .build()

    fun verifyAccess(token: String): JwtClaims = verify(token, TokenType.ACCESS)

    fun verifyRefresh(token: String): JwtClaims = verify(token, TokenType.REFRESH)

    fun verify(token: String, expectedType: TokenType): JwtClaims {
        val decoded = decode(token)
        val claims = decoded.toClaims()
        if (claims.type != expectedType) throw TokenException.WrongType(claims.type.wire)
        return claims
    }

    fun decode(token: String): DecodedJWT =
        try {
            delegate.verify(token)
        } catch (e: TokenExpiredException) {
            throw TokenException.Expired
        } catch (e: JWTVerificationException) {
            throw TokenException.Invalid
        } catch (e: IllegalArgumentException) {
            throw TokenException.Invalid
        }

    private fun DecodedJWT.toClaims(): JwtClaims {
        val subject = subject ?: throw TokenException.Invalid
        val deviceId = getClaim(JwtIssuer.CLAIM_DEVICE).asString() ?: throw TokenException.Invalid
        val version = getClaim(JwtIssuer.CLAIM_VERSION).asInt() ?: throw TokenException.Invalid
        val type = TokenType.fromWire(getClaim(JwtIssuer.CLAIM_TYPE).asString())
            ?: throw TokenException.Invalid
        return JwtClaims(
            subject = subject,
            deviceId = deviceId,
            tokenVersion = version,
            type = type,
            jti = id ?: throw TokenException.Invalid,
            issuedAt = issuedAtAsInstant?.epochSecond ?: 0,
            expiresAt = expiresAtAsInstant?.epochSecond ?: 0,
            issuer = issuer ?: "",
            audience = audience?.firstOrNull() ?: "",
        )
    }
}
