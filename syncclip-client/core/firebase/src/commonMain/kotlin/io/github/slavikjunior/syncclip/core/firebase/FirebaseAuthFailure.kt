package io.github.slavikjunior.syncclip.core.firebase

/**
 * Причина отказа Firebase Auth.
 *
 * Платформенные коды (`ERROR_INVALID_CREDENTIALS` и т. п.) не покидают
 * `core:firebase`: презентация маппит эти значения в строки ресурсов.
 */
enum class FirebaseAuthFailure {
    INVALID_CREDENTIALS,
    EMAIL_ALREADY_IN_USE,
    WEAK_PASSWORD,
    INVALID_EMAIL,
    EMAIL_NOT_VERIFIED,
    NETWORK,
    NOT_SUPPORTED,
    UNKNOWN,
}
