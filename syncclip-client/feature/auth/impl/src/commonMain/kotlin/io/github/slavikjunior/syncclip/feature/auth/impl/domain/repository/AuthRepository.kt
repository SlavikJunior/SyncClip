package io.github.slavikjunior.syncclip.feature.auth.impl.domain.repository

import io.github.slavikjunior.syncclip.feature.auth.impl.domain.model.AuthResult

/**
 * Вход и регистрация: Firebase Auth выдаёт ID token, сервер — свои JWT.
 *
 * Сессия сохраняется в `:core:datastore`, поэтому повторный запуск приложения
 * не требует входа.
 */
internal interface AuthRepository {

    suspend fun signIn(email: String, password: String): AuthResult

    suspend fun signUp(email: String, password: String): AuthResult

    /** Вход по Google ID token (Android Credential Manager, iOS GoogleSignIn). */
    suspend fun signInWithGoogle(idToken: String, accessToken: String? = null): AuthResult

    suspend fun signOut()

    /** Регистрирует текущий FCM-токен на сервере (`POST /fcm/register`). */
    suspend fun registerPushToken()
}
