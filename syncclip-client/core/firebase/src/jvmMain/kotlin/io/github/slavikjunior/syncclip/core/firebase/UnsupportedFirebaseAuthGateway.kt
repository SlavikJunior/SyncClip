package io.github.slavikjunior.syncclip.core.firebase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Desktop: Firebase client SDK не поддерживает JVM.
 *
 * Заглушка нужна, чтобы общий код собирался и работал: Desktop-клиент
 * авторизуется паролем через REST (см. docs/FIREBASE_SETUP.md) и не получает
 * push — у платформы нет FCM.
 */
internal class UnsupportedFirebaseAuthGateway : FirebaseAuthGateway {

    override val authState: Flow<FirebaseUser?> = emptyFlow()

    override suspend fun currentUser(): FirebaseUser? = null

    override suspend fun signUpWithEmail(email: String, password: String): FirebaseAuthResult =
        unsupported()

    override suspend fun signInWithEmail(email: String, password: String): FirebaseAuthResult =
        unsupported()

    override suspend fun signInWithGoogle(idToken: String, accessToken: String?): FirebaseAuthResult =
        unsupported()

    override suspend fun sendPasswordReset(email: String): FirebaseOperationResult =
        FirebaseOperationResult.Failure(FirebaseAuthFailure.NOT_SUPPORTED)

    override suspend fun idToken(forceRefresh: Boolean): String? = null

    override suspend fun signOut() = Unit

    private fun unsupported(): FirebaseAuthResult =
        FirebaseAuthResult.Failure(FirebaseAuthFailure.NOT_SUPPORTED)
}
