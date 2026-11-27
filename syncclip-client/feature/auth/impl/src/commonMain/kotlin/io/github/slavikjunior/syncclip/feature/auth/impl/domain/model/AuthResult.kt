package io.github.slavikjunior.syncclip.feature.auth.impl.domain.model

/** Результат входа/регистрации на уровне домена. */
internal sealed interface AuthResult {

    data object Success : AuthResult

    data class Failure(val reason: AuthFailureReason) : AuthResult
}
