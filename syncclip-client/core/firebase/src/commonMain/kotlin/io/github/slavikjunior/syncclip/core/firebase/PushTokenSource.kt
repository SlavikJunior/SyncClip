package io.github.slavikjunior.syncclip.core.firebase

import kotlinx.coroutines.flow.Flow

/**
 * Источник FCM registration token.
 *
 * Токен меняется при переустановке, очистке данных и ротации на стороне FCM,
 * поэтому подписка — `Flow`, а не однократное чтение. Регистрация токена на
 * сервере (`POST /fcm/register`) — задача слоя фичи.
 */
interface PushTokenSource {

    /** Обновления токена; новые значения приходят после ротации. */
    val tokens: Flow<String>

    suspend fun currentToken(): String?

    /**
     * Запрос разрешения на уведомления (iOS — системный диалог, Android 13+ —
     * runtime-разрешение на уровне Activity).
     */
    suspend fun requestPermission(): Boolean
}
