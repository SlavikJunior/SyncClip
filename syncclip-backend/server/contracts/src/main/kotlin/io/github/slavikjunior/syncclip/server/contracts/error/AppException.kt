package io.github.slavikjunior.syncclip.server.contracts.error

/**
 * Единая иерархия доменных исключений (дизайн §4.3). Слой API не знает
 * деталей реализации — исключение маппится в HTTP только в StatusPages.
 */
sealed class AppException(
    val code: ErrorCode,
    val details: Map<String, String> = emptyMap(),
    val retryAfterSeconds: Long? = null,
    cause: Throwable? = null,
) : RuntimeException(code.restCode, cause)

class ValidationException(field: String, reason: String, cause: Throwable? = null) :
    AppException(ErrorCode.VALIDATION_ERROR, mapOf("field" to field, "reason" to reason), cause = cause)

class UnauthorizedException(
    code: ErrorCode = ErrorCode.TOKEN_INVALID,
    cause: Throwable? = null,
) : AppException(code, cause = cause)

class ForbiddenException(
    code: ErrorCode,
    details: Map<String, String> = emptyMap(),
    retryAfterSeconds: Long? = null,
) : AppException(code, details, retryAfterSeconds)

class NotFoundException(code: ErrorCode) : AppException(code)

class ConflictException(code: ErrorCode) : AppException(code)

/** Контрактные коды гостевых приглашений (FUNC-10): 400 с конкретным `error`. */
class GuestInviteException(code: ErrorCode) : AppException(code)

class RateLimitedException(retryAfterSeconds: Long) :
    AppException(ErrorCode.RATE_LIMIT_EXCEEDED, retryAfterSeconds = retryAfterSeconds)

class PayloadTooLargeException(maxBytes: Long) :
    AppException(ErrorCode.PAYLOAD_TOO_LARGE, mapOf("maxBytes" to maxBytes.toString()))
