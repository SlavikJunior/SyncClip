package io.github.slavikjunior.syncclip.app.android.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.slavikjunior.syncclip.app.android.R

/**
 * Канал и показ уведомлений SyncClip.
 *
 * Сервер присылает `notification` + `data`; в фоне систему уведомляет сам FCM,
 * поэтому [show] вызывается только когда приложение на экране.
 */
internal object SyncClipNotifications {

    const val CHANNEL_ID = "syncclip_alerts"
    private const val CHANNEL_NAME = "SyncClip"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun show(context: Context, title: String, body: String) {
        // Проверка встроена в метод: lint (MissingPermission) не видит её в helper.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(nextNotificationId(), notification)
    }

    private fun nextNotificationId(): Int = nextId.getAndIncrement()

    private const val NOTIFICATION_ID_START = 1001

    /** Каждый push — отдельное уведомление, а не замена предыдущего. */
    private val nextId = java.util.concurrent.atomic.AtomicInteger(NOTIFICATION_ID_START)
}
