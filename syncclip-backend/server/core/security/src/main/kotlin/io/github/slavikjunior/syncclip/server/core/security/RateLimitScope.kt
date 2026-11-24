package io.github.slavikjunior.syncclip.server.core.security

/** Области лимитов из §12.4: ключ (IP/userId/deviceId) выбирает вызывающий код. */
enum class RateLimitScope {
    LOGIN,
    REGISTER,
    REFRESH,
    LOGOUT,
    CANARY,
    CANARY_ATTEMPT,
    GUEST_INVITE,
    GUEST_EXCHANGE,
    DEFAULT,
    SYNC_PENDING,
    SYNC_EVENT,
    ACK,
    PING,
}
