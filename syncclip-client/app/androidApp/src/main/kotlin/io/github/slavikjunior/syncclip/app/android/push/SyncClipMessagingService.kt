package io.github.slavikjunior.syncclip.app.android.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.slavikjunior.syncclip.core.firebase.FirebasePushTokens

/**
 * FCM в Android-приложении (ARCHITECTURE.md §4).
 *
 * Новый токен публикуется в общий канал: `PushTokenSource` раздаёт его
 * подписчикам, а composition root регистрирует на сервере.
 */
class SyncClipMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        FirebasePushTokens.publish(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data[KEY_TITLE]
            ?: return
        val body = message.notification?.body
            ?: message.data[KEY_BODY].orEmpty()
        SyncClipNotifications.show(this, title = title, body = body)
    }

    private companion object {
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
    }
}
