package io.github.slavikjunior.syncclip.feature.auth.impl.domain.model

/** Причина отказа входа в терминах UI; строки живут в ресурсах фичи. */
internal enum class AuthFailureReason {
    INVALID_CREDENTIALS,
    EMAIL_ALREADY_IN_USE,
    WEAK_PASSWORD,
    INVALID_EMAIL,
    INVALID_INPUT,
    EMAIL_NOT_VERIFIED,
    NETWORK,
    NOT_SUPPORTED,
    UNKNOWN,
}
