package io.github.slavikjunior.syncclip.feature.auth.impl.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.slavikjunior.syncclip.feature.auth.impl.domain.model.AuthFailureReason
import io.github.slavikjunior.syncclip.feature.auth.impl.domain.model.AuthResult
import io.github.slavikjunior.syncclip.feature.auth.impl.domain.repository.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI-состояние экрана входа.
 *
 * Состояние мутируется только `_state.update { }` (INV-09); навигация и
 * сообщения — через `Channel` (INV-11).
 */
internal class AuthViewModel(
    private val repository: AuthRepository,
) : ViewModel() {

    private val currentState = MutableStateFlow(AuthUiState())

    val state: StateFlow<AuthUiState> = currentState.asStateFlow()

    private val effects = Channel<AuthEffect>(Channel.BUFFERED)

    val effect: Flow<AuthEffect> = effects.receiveAsFlow()

    fun handleIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.EmailChanged -> currentState.update {
                it.copy(email = intent.value, failure = null)
            }

            is AuthIntent.PasswordChanged -> currentState.update {
                it.copy(password = intent.value, failure = null)
            }

            AuthIntent.ToggleMode -> currentState.update {
                it.copy(
                    mode = if (it.mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN,
                    failure = null,
                )
            }

            AuthIntent.Submit -> submit()
        }
    }

    private fun submit() {
        val snapshot = currentState.value
        if (!snapshot.canSubmit) return

        currentState.update { it.copy(isSubmitting = true, failure = null) }
        viewModelScope.launch {
            // Вход трогает DataStore (сохранение сессии, идентичность
            // устройства) и сеть: сбой диска не должен ронять приложение на
            // экране входа и оставлять кнопку «Войти» заблокированной.
            val result = runCatching {
                when (snapshot.mode) {
                    AuthMode.SIGN_IN -> repository.signIn(snapshot.email, snapshot.password)
                    AuthMode.SIGN_UP -> repository.signUp(snapshot.email, snapshot.password)
                }
            }.getOrElse { AuthResult.Failure(AuthFailureReason.UNKNOWN) }
            when (result) {
                AuthResult.Success -> {
                    currentState.update { it.copy(isSubmitting = false, password = "") }
                    effects.send(AuthEffect.Authenticated)
                }

                is AuthResult.Failure -> {
                    currentState.update { it.copy(isSubmitting = false, failure = result.reason) }
                    effects.send(AuthEffect.ShowMessage(result.reason))
                }
            }
        }
    }
}
