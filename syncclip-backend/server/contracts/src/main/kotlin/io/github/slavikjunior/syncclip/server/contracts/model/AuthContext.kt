package io.github.slavikjunior.syncclip.server.contracts.model

/** Проверенный контекст запроса: пользователь + устройство из JWT и БД. */
data class AuthContext(
    val userId: String,
    val deviceId: String,
    val deviceName: String,
    val deviceType: DeviceType,
) {
    val isGuest: Boolean get() = deviceType == DeviceType.GUEST
}
