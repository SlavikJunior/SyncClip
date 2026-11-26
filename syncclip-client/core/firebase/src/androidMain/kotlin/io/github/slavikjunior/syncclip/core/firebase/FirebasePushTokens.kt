package io.github.slavikjunior.syncclip.core.firebase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Канал FCM-токена внутри процесса.
 *
 * `FirebaseMessagingService` живёт в `:app:androidApp` (ARCHITECTURE.md §4) и
 * публикует сюда каждый новый токен; `PushTokenSource` раздаёт их подписчикам.
 */
public object FirebasePushTokens {

    private val state = MutableStateFlow<String?>(null)

    public val tokens: StateFlow<String?> = state.asStateFlow()

    /** Вызывается из `FirebaseMessagingService.onNewToken` и при первом получении. */
    public fun publish(token: String) {
        state.value = token
    }
}
