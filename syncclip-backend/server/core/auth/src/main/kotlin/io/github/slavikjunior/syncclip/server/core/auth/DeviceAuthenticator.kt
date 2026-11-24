package io.github.slavikjunior.syncclip.server.core.auth

import io.github.slavikjunior.syncclip.server.contracts.error.ErrorCode
import io.github.slavikjunior.syncclip.server.contracts.error.UnauthorizedException
import io.github.slavikjunior.syncclip.server.contracts.model.AuthContext
import io.github.slavikjunior.syncclip.server.contracts.model.DeviceType
import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.findDeviceById
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.touchDeviceLastSeen
import io.github.slavikjunior.syncclip.server.core.database.tx
import java.time.Duration
import java.time.Instant

/**
 * Шаги 3–6 пайплайна проверки access-токена (§7.1): устройство существует,
 * принадлежит субъекту, token_version совпадает, гость не истёк.
 *
 * Живёт в отдельном `core:auth`, а не в `core:database`: это auth-политика,
 * инфраструктура БД не должна диктовать правила доступа (ARCH-01).
 */
class DeviceAuthenticator(
    private val databaseFactory: DatabaseFactory,
    private val lastSeenInterval: Duration = Duration.ofSeconds(60),
) {

    suspend fun authenticate(
        userId: String,
        deviceId: String,
        tokenVersion: Int,
        now: Instant = Instant.now(),
    ): AuthContext = tx(databaseFactory.database) {
        val device = findDeviceById(userId, deviceId)
            ?: throw UnauthorizedException(ErrorCode.DEVICE_REVOKED)
        if (device.userId != userId) throw UnauthorizedException(ErrorCode.TOKEN_INVALID)
        if (device.tokenVersion != tokenVersion) throw UnauthorizedException(ErrorCode.TOKEN_REVOKED)
        val expiresAt = device.expiresAt
        if (device.isGuest && (expiresAt == null || !expiresAt.isAfter(now))) {
            throw UnauthorizedException(ErrorCode.GUEST_SESSION_EXPIRED)
        }
        // last_seen_at обновляем не чаще раза в минуту (§8.5): не пишем на каждый кадр.
        if (Duration.between(device.lastSeenAt, now) >= lastSeenInterval) {
            touchDeviceLastSeen(userId, deviceId, now)
        }
        AuthContext(
            userId = device.userId,
            deviceId = device.id,
            deviceName = device.name,
            deviceType = DeviceType.fromWire(device.deviceType) ?: DeviceType.PERMANENT,
        )
    }
}
