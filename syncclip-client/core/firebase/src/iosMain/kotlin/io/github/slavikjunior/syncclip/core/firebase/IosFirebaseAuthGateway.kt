package io.github.slavikjunior.syncclip.core.firebase

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * iOS-реализация через [IosFirebaseBridge].
 *
 * Пока Swift не установил делегат, любая операция возвращает `NOT_SUPPORTED`:
 * приложение не падает, но UI показывает понятную ошибку вместо краха.
 */
internal class IosFirebaseAuthGateway(
    private val bridge: () -> IosFirebaseBridgeDelegate? = IosFirebaseBridge::delegateOrNull,
) : FirebaseAuthGateway {

    override val authState: Flow<FirebaseUser?> = callbackFlow {
        val delegate = bridge() ?: run {
            trySend(null)
            close()
            return@callbackFlow
        }
        val subscription = delegate.observeAuth { user -> trySend(user) }
        awaitClose { subscription.dispose() }
    }

    override suspend fun currentUser(): FirebaseUser? = bridge()?.currentUser()

    override suspend fun signUpWithEmail(email: String, password: String): FirebaseAuthResult =
        delegateOrFailure { delegate ->
            awaitCallback { completion ->
                delegate.signUpWithEmail(email = email, password = password, completion = completion)
            }
        }

    override suspend fun signInWithEmail(email: String, password: String): FirebaseAuthResult =
        delegateOrFailure { delegate ->
            awaitCallback { completion ->
                delegate.signInWithEmail(email = email, password = password, completion = completion)
            }
        }

    override suspend fun signInWithGoogle(
        idToken: String,
        accessToken: String?,
    ): FirebaseAuthResult = delegateOrFailure { delegate ->
        awaitCallback { completion ->
            delegate.signInWithGoogle(
                idToken = idToken,
                accessToken = accessToken,
                completion = completion,
            )
        }
    }

    override suspend fun sendPasswordReset(email: String): FirebaseOperationResult {
        val delegate = bridge() ?: return FirebaseOperationResult.Failure(FirebaseAuthFailure.NOT_SUPPORTED)
        return awaitCallback { completion -> delegate.sendPasswordReset(email = email, completion = completion) }
    }

    override suspend fun idToken(forceRefresh: Boolean): String? {
        val delegate = bridge() ?: return null
        return suspendCancellableCoroutine { continuation ->
            delegate.idToken(forceRefresh = forceRefresh) { token ->
                if (continuation.isActive) continuation.resume(token)
            }
        }
    }

    override suspend fun signOut() {
        bridge()?.signOut()
    }

    private suspend fun delegateOrFailure(
        block: suspend (IosFirebaseBridgeDelegate) -> FirebaseAuthResult,
    ): FirebaseAuthResult {
        val delegate = bridge()
            ?: return FirebaseAuthResult.Failure(FirebaseAuthFailure.NOT_SUPPORTED)
        return block(delegate)
    }
}

/** Ожидание ответа Swift-реализации без ручного `DispatchQueue`. */
private suspend fun <T> awaitCallback(
    call: ((T) -> Unit) -> Unit,
): T = suspendCancellableCoroutine { continuation ->
    call { value ->
        if (continuation.isActive) continuation.resume(value)
    }
}
