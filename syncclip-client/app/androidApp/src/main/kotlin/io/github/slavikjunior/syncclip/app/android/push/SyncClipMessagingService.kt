package io.github.slavikjunior.syncclip.app.android.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.slavikjunior.syncclip.core.datastore.AppSettingsStore
import io.github.slavikjunior.syncclip.core.firebase.FirebasePushTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * FCM в Android-приложении (ARCHITECTURE.md §4).
 *
 * Новый токен публикуется в общий канал: `PushTokenSource` раздаёт его
 * подписчикам, а composition root регистрирует на сервере.
 *
 * US-009: сервер отправляет push всегда, клиент решает, показывать ли его.
 * Настройки читаются асинхронно: DataStore не должен блокировать callback FCM.
 */
class SyncClipMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNewToken(token: String) {
        FirebasePushTokens.publish(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data[KEY_TITLE]
            ?: return
        val body = message.notification?.body
            ?: message.data[KEY_BODY].orEmpty()
        val kind = message.data[KEY_TYPE]
        scope.launch {
            val settings = runCatching { GlobalContext.get().get<AppSettingsStore>() }
                .getOrNull()
                ?.let { store -> runCatching { store.current() }.getOrNull() }
                ?.notifications
            // Настройки недоступны (процесс только поднялся) — показываем:
            // лучше лишнее уведомление, чем пропущенное событие безопасности.
            if (settings != null && !isEnabled(kind, settings.newDevice, settings.failedSignIn)) {
                return@launch
            }
            SyncClipNotifications.show(
                context = this@SyncClipMessagingService,
                title = title,
                body = body,
                sound = settings?.sound ?: true,
                vibration = settings?.vibration ?: true,
            )
        }
    }

    private fun isEnabled(kind: String?, newDevice: Boolean, failedSignIn: Boolean): Boolean =
        when (kind) {
            TYPE_NEW_DEVICE -> newDevice
            TYPE_FAILED_SIGN_IN -> failedSignIn
            else -> true
        }

    private companion object {
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_TYPE = "type"
        const val TYPE_NEW_DEVICE = "new_device"
        const val TYPE_FAILED_SIGN_IN = "master_password_failed"
    }
}
