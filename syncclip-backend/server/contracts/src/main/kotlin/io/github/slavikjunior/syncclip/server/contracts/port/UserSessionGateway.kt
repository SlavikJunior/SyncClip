package io.github.slavikjunior.syncclip.server.contracts.port

import io.github.slavikjunior.syncclip.server.contracts.ws.RevocationReason
import io.github.slavikjunior.syncclip.server.contracts.ws.ServerWsMessage

/**
 * Порт доставки/ревокации WS-сессий. Реализация — feature:sync,
 * связывание — app: feature:devices не имеет права зависеть от feature:sync
 * (R-1), поэтому контракт живёт в contracts (дизайн §3.4).
 *
 * Все методы адресуют устройство парой `(userId, deviceId)`: после SEC-09
 * deviceId уникален только внутри аккаунта.
 */
interface UserSessionGateway {
    fun isOnline(userId: String, deviceId: String): Boolean

    suspend fun deliver(userId: String, deviceId: String, message: ServerWsMessage): Boolean

    suspend fun broadcast(userId: String, message: ServerWsMessage, exceptDeviceId: String? = null)

    suspend fun revokeDevice(userId: String, deviceId: String, reason: RevocationReason)

    suspend fun revokeAllOfUser(userId: String, reason: RevocationReason)
}

/** Заглушка для тестов/сборки, пока реестр сессий не подключён (этап M2). */
object NoopUserSessionGateway : UserSessionGateway {
    override fun isOnline(userId: String, deviceId: String): Boolean = false
    override suspend fun deliver(userId: String, deviceId: String, message: ServerWsMessage): Boolean = false
    override suspend fun broadcast(userId: String, message: ServerWsMessage, exceptDeviceId: String?) = Unit
    override suspend fun revokeDevice(userId: String, deviceId: String, reason: RevocationReason) = Unit
    override suspend fun revokeAllOfUser(userId: String, reason: RevocationReason) = Unit
}
