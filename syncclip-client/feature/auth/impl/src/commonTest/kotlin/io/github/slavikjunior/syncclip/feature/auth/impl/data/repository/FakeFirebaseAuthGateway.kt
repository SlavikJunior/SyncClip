package io.github.slavikjunior.syncclip.feature.auth.impl.data.repository

import io.github.slavikjunior.syncclip.core.firebase.FirebaseAuthFailure
import io.github.slavikjunior.syncclip.core.firebase.FirebaseAuthGateway
import io.github.slavikjunior.syncclip.core.firebase.FirebaseAuthResult
import io.github.slavikjunior.syncclip.core.firebase.FirebaseOperationResult
import io.github.slavikjunior.syncclip.core.firebase.FirebaseUser
import io.github.slavikjunior.syncclip.core.firebase.PushTokenSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

internal class FakeFirebaseAuthGateway(
    var signInResult: FirebaseAuthResult = FirebaseAuthResult.Success(
        FirebaseUser(uid = "uid-1", email = "user@example.com"),
    ),
    var token: String? = "firebase-id-token",
) : FirebaseAuthGateway {

    var signedOut: Boolean = false
    private val user = MutableStateFlow<FirebaseUser?>(null)

    override val authState: Flow<FirebaseUser?> = user

    override suspend fun currentUser(): FirebaseUser? = user.value

    override suspend fun signUpWithEmail(email: String, password: String): FirebaseAuthResult =
        signInResult

    override suspend fun signInWithEmail(email: String, password: String): FirebaseAuthResult =
        signInResult

    override suspend fun signInWithGoogle(idToken: String, accessToken: String?): FirebaseAuthResult =
        signInResult

    override suspend fun sendPasswordReset(email: String): FirebaseOperationResult =
        FirebaseOperationResult.Success

    override suspend fun idToken(forceRefresh: Boolean): String? = token

    override suspend fun signOut() {
        signedOut = true
        user.value = null
    }
}

internal fun firebaseFailure(reason: FirebaseAuthFailure): FirebaseAuthResult =
    FirebaseAuthResult.Failure(reason)

internal class FakePushTokenSource(
    var token: String? = "fcm-token",
) : PushTokenSource {

    override val tokens: Flow<String> = emptyFlow()

    override suspend fun currentToken(): String? = token

    override suspend fun requestPermission(): Boolean = true
}
