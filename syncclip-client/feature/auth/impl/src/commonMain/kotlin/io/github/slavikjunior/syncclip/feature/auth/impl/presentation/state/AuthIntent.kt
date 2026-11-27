package io.github.slavikjunior.syncclip.feature.auth.impl.presentation.state

/** Действия пользователя на экране входа (INV-10). */
internal sealed interface AuthIntent {

    data class EmailChanged(val value: String) : AuthIntent

    data class PasswordChanged(val value: String) : AuthIntent

    data object Submit : AuthIntent

    data object ToggleMode : AuthIntent
}
