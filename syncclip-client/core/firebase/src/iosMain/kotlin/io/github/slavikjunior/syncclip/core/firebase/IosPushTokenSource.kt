package io.github.slavikjunior.syncclip.core.firebase

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * FCM-токен iOS: APNs → Firebase Messaging в Swift.
 *
 * Если Swift-мост ещё не установлен, подписка пустая, а токен — `null`:
 * push не ломает остальной сценарий входа.
 */
internal class IosPushTokenSource(
    private val bridge: () -> IosFirebaseBridgeDelegate? = IosFirebaseBridge::delegateOrNull,
) : PushTokenSource {

    override val tokens: Flow<String> = callbackFlow {
        val delegate = bridge() ?: run {
            close()
            return@callbackFlow
        }
        val subscription = delegate.observeMessagingToken { token -> trySend(token) }
        awaitClose { subscription.dispose() }
    }

    override suspend fun currentToken(): String? {
        val delegate = bridge() ?: return null
        return suspendCancellableCoroutine { continuation ->
            delegate.messagingToken { token ->
                if (continuation.isActive) continuation.resume(token)
            }
        }
    }

    override suspend fun requestPermission(): Boolean {
        val delegate = bridge() ?: return false
        return suspendCancellableCoroutine { continuation ->
            delegate.requestNotificationPermission { granted ->
                if (continuation.isActive) continuation.resume(granted)
            }
        }
    }
}

/** Заглушка для iOS без установленного моста (используется в тестах). */
internal object EmptyPushTokenSource : PushTokenSource {
    override val tokens: Flow<String> = emptyFlow()
    override suspend fun currentToken(): String? = null
    override suspend fun requestPermission(): Boolean = false
}
