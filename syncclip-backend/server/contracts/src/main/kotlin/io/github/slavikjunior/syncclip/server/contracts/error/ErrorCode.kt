package io.github.slavikjunior.syncclip.server.contracts.error

/**
 * Каталог ошибок REST-слоя: HTTP-статус + snake_case-код (дизайн §6.3).
 * WS-коды — отдельное пространство [WsErrorCode] (CG-32).
 */
enum class ErrorCode(
    val httpStatus: Int,
    val restCode: String,
    val wsCode: WsErrorCode? = null,
) {
    VALIDATION_ERROR(400, "validation_error", WsErrorCode.INVALID_PACKET),
    EMAIL_ALREADY_REGISTERED(409, "email_already_registered"),
    DEVICE_ID_CONFLICT(409, "device_id_conflict"),
    INVALID_CREDENTIALS(401, "invalid_credentials"),
    FIREBASE_TOKEN_INVALID(401, "firebase_token_invalid"),
    EMAIL_NOT_VERIFIED(403, "email_not_verified"),
    TOKEN_INVALID(401, "token_invalid", WsErrorCode.UNAUTHORIZED),
    TOKEN_EXPIRED(401, "token_expired", WsErrorCode.UNAUTHORIZED),
    TOKEN_REVOKED(401, "token_revoked", WsErrorCode.UNAUTHORIZED),
    REFRESH_TOKEN_REUSED(401, "refresh_token_reused"),
    DEVICE_REVOKED(401, "device_revoked", WsErrorCode.UNAUTHORIZED),
    GUEST_SESSION_EXPIRED(401, "guest_session_expired", WsErrorCode.SESSION_EXPIRED),
    CANARY_NOT_FOUND(404, "canary_not_found"),
    CANARY_ALREADY_SET(409, "canary_already_set"),
    MASTER_PASSWORD_LOCKED(403, "master_password_locked"),
    DEVICE_LIMIT_EXCEEDED(403, "device_limit_exceeded", WsErrorCode.DEVICE_LIMIT_EXCEEDED),
    DEVICE_NOT_FOUND(404, "device_not_found", WsErrorCode.DEVICE_NOT_FOUND),
    DEVICE_MISMATCH(403, "device_mismatch"),
    FORBIDDEN_FOR_GUEST(403, "forbidden_for_guest"),
    INVALID_GUEST_INVITE(400, "invalid_guest_invite"),
    GUEST_INVITE_EXPIRED(400, "guest_invite_expired"),
    GUEST_INVITE_ALREADY_USED(400, "guest_invite_already_used"),
    PAYLOAD_TOO_LARGE(413, "payload_too_large", WsErrorCode.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE(415, "unsupported_media_type"),
    RATE_LIMIT_EXCEEDED(429, "rate_limit_exceeded", WsErrorCode.RATE_LIMIT_EXCEEDED),
    METHOD_NOT_ALLOWED(405, "method_not_allowed"),
    NOT_FOUND(404, "not_found"),
    INTERNAL_ERROR(500, "internal_error", WsErrorCode.INTERNAL_ERROR),
}
