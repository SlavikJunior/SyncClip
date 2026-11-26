package io.github.slavikjunior.syncclip.core.firebase

import kotlinx.coroutines.flow.Flow

/**
 * Аутентификация через Firebase Auth.
 *
 * Контракт общий для всех платформ: Android использует Play services SDK,
 * iOS — мост в Swift-реализацию (`IosFirebaseBridge`), Desktop возвращает
 * `NOT_SUPPORTED`. Firebase ID token нужен серверу (`POST /auth/firebase`).
 */
interface FirebaseAuthGateway {

    /** Состояние сессии Firebase; `null` — пользователь вышел. */
    val authState: Flow<FirebaseUser?>

    suspend fun currentUser(): FirebaseUser?

    suspend fun signUpWithEmail(email: String, password: String): FirebaseAuthResult

    suspend fun signInWithEmail(email: String, password: String): FirebaseAuthResult

    /** Вход по Google ID token (Android Credential Manager / iOS GoogleSignIn). */
    suspend fun signInWithGoogle(idToken: String, accessToken: String? = null): FirebaseAuthResult

    suspend fun sendPasswordReset(email: String): FirebaseOperationResult

    /** Firebase ID token; `forceRefresh = true` игнорирует кэш SDK. */
    suspend fun idToken(forceRefresh: Boolean = false): String?

    suspend fun signOut()
}
