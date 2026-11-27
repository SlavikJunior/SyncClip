package io.github.slavikjunior.syncclip.feature.auth.impl.presentation.state

import io.github.slavikjunior.syncclip.feature.auth.impl.domain.model.AuthFailureReason

/** Состояние экрана входа: поля формы, режим, отправка и последняя ошибка. */
internal data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val mode: AuthMode = AuthMode.SIGN_IN,
    val isSubmitting: Boolean = false,
    val failure: AuthFailureReason? = null,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.length >= MIN_PASSWORD_LENGTH && !isSubmitting

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
    }
}
