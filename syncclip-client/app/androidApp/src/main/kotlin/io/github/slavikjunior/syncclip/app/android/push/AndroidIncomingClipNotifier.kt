package io.github.slavikjunior.syncclip.app.android.push

import android.content.Context
import io.github.slavikjunior.syncclip.app.android.R
import io.github.slavikjunior.syncclip.core.datastore.AppSettingsStore
import io.github.slavikjunior.syncclip.core.platform.IncomingClipNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Локальное уведомление о копии с другого устройства (US-009).
 *
 * Решение «показывать ли» уже принято в `:feature:sync:impl` по настройкам;
 * здесь читаются только звук и вибрация. Чтение DataStore — в фоне: вызов
 * приходит из receiveLoop WebSocket и не должен его задерживать.
 */
internal class AndroidIncomingClipNotifier(
    private val context: Context,
    private val settings: AppSettingsStore,
) : IncomingClipNotifier {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun notifyIncoming(originDeviceName: String?) {
        scope.launch {
            val notifications = runCatching { settings.current().notifications }.getOrNull()
                ?: return@launch
            if (!notifications.newCopies) return@launch
            val title = context.getString(R.string.push_new_clip_title)
            val body = originDeviceName
                ?.let { name -> context.getString(R.string.push_new_clip_body, name) }
                ?: context.getString(R.string.push_new_clip_body_unknown)
            SyncClipNotifications.show(
                context = context,
                title = title,
                body = body,
                sound = notifications.sound,
                vibration = notifications.vibration,
            )
        }
    }
}
