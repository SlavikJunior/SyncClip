package io.github.slavikjunior.syncclip.core.firebase

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull

/**
 * FCM-токен Android.
 *
 * Разрешение POST_NOTIFICATIONS запрашивается в `MainActivity` (нужен Activity),
 * поэтому здесь возвращается `true`: к моменту регистрации токена приложение уже
 * прошло запрос.
 */
internal class AndroidPushTokenSource : PushTokenSource {

    override val tokens: Flow<String> = FirebasePushTokens.tokens.filterNotNull()

    override suspend fun currentToken(): String? {
        FirebasePushTokens.tokens.value?.let { return it }
        return runCatching { FirebaseMessaging.getInstance().token.awaitResult() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.also(FirebasePushTokens::publish)
    }

    override suspend fun requestPermission(): Boolean = true
}
