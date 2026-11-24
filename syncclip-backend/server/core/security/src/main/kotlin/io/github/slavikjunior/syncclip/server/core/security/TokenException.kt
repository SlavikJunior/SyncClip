package io.github.slavikjunior.syncclip.server.core.security

/** Причина отказа JWT; маппинг в HTTP/WS-код — в слое API. */
sealed class TokenException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    data object Expired : TokenException("token_expired")
    data object Invalid : TokenException("token_invalid")
    data class WrongType(val actual: String?) : TokenException("token_invalid: typ=$actual")
}
