package io.github.slavikjunior.syncclip.server.core.security

import com.auth0.jwt.interfaces.Payload

/** Проверенные claims access/refresh-токена (дизайн §7.1). */
data class JwtClaims(
    val subject: String,
    val deviceId: String,
    val tokenVersion: Int,
    val type: TokenType,
    val jti: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val issuer: String,
    val audience: String,
) {
    companion object {
        /**
         * Единый маппинг `JWTPrincipal.payload` → [JwtClaims] (ARCH-02).
         * Невалидные обязательные claims — [TokenException.Invalid].
         */
        fun fromPayload(payload: Payload): JwtClaims = JwtClaims(
            subject = payload.subject ?: throw TokenException.Invalid,
            deviceId = payload.getClaim(JwtIssuer.CLAIM_DEVICE).asString() ?: throw TokenException.Invalid,
            tokenVersion = payload.getClaim(JwtIssuer.CLAIM_VERSION).asInt() ?: throw TokenException.Invalid,
            type = TokenType.fromWire(payload.getClaim(JwtIssuer.CLAIM_TYPE).asString())
                ?: throw TokenException.Invalid,
            jti = payload.id ?: throw TokenException.Invalid,
            issuedAt = payload.issuedAt?.time?.div(1000) ?: 0,
            expiresAt = payload.expiresAt?.time?.div(1000) ?: 0,
            issuer = payload.issuer ?: "",
            audience = payload.audience?.firstOrNull() ?: "",
        )
    }
}

enum class TokenType(val wire: String) {
    ACCESS("access"),
    REFRESH("refresh"),
    ;

    companion object {
        fun fromWire(value: String?): TokenType? = entries.firstOrNull { it.wire == value }
    }
}
