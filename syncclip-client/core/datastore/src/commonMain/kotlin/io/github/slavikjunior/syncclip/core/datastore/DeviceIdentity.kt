package io.github.slavikjunior.syncclip.core.datastore

/**
 * Идентичность устройства для сервера.
 *
 * `deviceId` генерируется один раз и сохраняется: сервер привязывает к нему
 * JWT-сессию, FCM-токен и офлайн-очередь.
 */
data class DeviceIdentity(
    val deviceId: String,
    val deviceName: String,
    val osType: String,
)
