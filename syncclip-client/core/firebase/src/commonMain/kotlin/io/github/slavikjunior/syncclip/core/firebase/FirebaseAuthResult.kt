package io.github.slavikjunior.syncclip.core.firebase

/** Результат входа/регистрации: пользователь либо типизированный отказ. */
sealed interface FirebaseAuthResult {

    data class Success(val user: FirebaseUser) : FirebaseAuthResult

    data class Failure(val reason: FirebaseAuthFailure) : FirebaseAuthResult
}

/** Результат операции без пользователя (сброс пароля). */
sealed interface FirebaseOperationResult {

    data object Success : FirebaseOperationResult

    data class Failure(val reason: FirebaseAuthFailure) : FirebaseOperationResult
}
