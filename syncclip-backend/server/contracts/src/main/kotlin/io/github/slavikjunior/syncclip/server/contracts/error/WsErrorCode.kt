package io.github.slavikjunior.syncclip.server.contracts.error

/** Коды server→client `ERROR` (WEBSOCKET_PROTOCOL §3.7 + CG-17). */
enum class WsErrorCode {
    INVALID_PACKET,
    DEVICE_LIMIT_EXCEEDED,
    OFFLINE_QUEUE_FULL,
    RATE_LIMIT_EXCEEDED,
    UNAUTHORIZED,
    DEVICE_NOT_FOUND,
    PAYLOAD_TOO_LARGE,
    INTERNAL_ERROR,
    SESSION_EXPIRED,
}
