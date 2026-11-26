package io.github.slavikjunior.syncclip.core.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser as FirebaseSdkUser
import com.google.firebase.auth.AuthResult as FirebaseSdkAuthResult
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Firebase Auth через Google Play services.
 *
 * `authState` — `callbackFlow` поверх `AuthStateListener`: первое значение
 * приходит сразу после подписки, поэтому UI не ждёт события входа.
 */
internal class AndroidFirebaseAuthGateway(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : FirebaseAuthGateway {

    override val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAppUser()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun currentUser(): FirebaseUser? = auth.currentUser?.toAppUser()

    override suspend fun signUpWithEmail(email: String, password: String): FirebaseAuthResult =
        runAuth { auth.createUserWithEmailAndPassword(email.trim(), password).awaitUser() }

    override suspend fun signInWithEmail(email: String, password: String): FirebaseAuthResult =
        runAuth { auth.signInWithEmailAndPassword(email.trim(), password).awaitUser() }

    override suspend fun signInWithGoogle(
        idToken: String,
        accessToken: String?,
    ): FirebaseAuthResult = runAuth {
        val credential = GoogleAuthProvider.getCredential(idToken, accessToken)
        auth.signInWithCredential(credential).awaitUser()
    }

    override suspend fun sendPasswordReset(email: String): FirebaseOperationResult =
        runCatching { auth.sendPasswordResetEmail(email.trim()).awaitResult() }
            .fold(
                onSuccess = { FirebaseOperationResult.Success },
                onFailure = { FirebaseOperationResult.Failure(it.toFailure()) },
            )

    override suspend fun idToken(forceRefresh: Boolean): String? = runCatching {
        auth.currentUser?.getIdToken(forceRefresh)?.awaitResult()?.token
    }.getOrNull()

    override suspend fun signOut() {
        auth.signOut()
    }

    private suspend fun runAuth(block: suspend () -> FirebaseSdkUser): FirebaseAuthResult =
        runCatching { block() }.fold(
            onSuccess = { FirebaseAuthResult.Success(it.toAppUser()) },
            onFailure = { FirebaseAuthResult.Failure(it.toFailure()) },
        )
}

private suspend fun Task<FirebaseSdkAuthResult>.awaitUser(): FirebaseSdkUser =
    awaitResult().user ?: error("Firebase Auth returned no user")

private fun FirebaseSdkUser.toAppUser(): FirebaseUser = FirebaseUser(
    uid = uid,
    email = email.orEmpty(),
    displayName = displayName,
    isEmailVerified = isEmailVerified,
)

private fun Throwable.toFailure(): FirebaseAuthFailure {
    val code = (this as? FirebaseAuthException)?.errorCode
    return when (code) {
        "ERROR_INVALID_CREDENTIALS",
        "ERROR_WRONG_PASSWORD",
        "ERROR_USER_NOT_FOUND",
        -> FirebaseAuthFailure.INVALID_CREDENTIALS

        "ERROR_EMAIL_ALREADY_IN_USE" -> FirebaseAuthFailure.EMAIL_ALREADY_IN_USE
        "ERROR_WEAK_PASSWORD" -> FirebaseAuthFailure.WEAK_PASSWORD
        "ERROR_INVALID_EMAIL" -> FirebaseAuthFailure.INVALID_EMAIL
        "ERROR_NETWORK_REQUEST_FAILED" -> FirebaseAuthFailure.NETWORK
        "ERROR_OPERATION_NOT_ALLOWED" -> FirebaseAuthFailure.NOT_SUPPORTED
        else -> FirebaseAuthFailure.UNKNOWN
    }
}
