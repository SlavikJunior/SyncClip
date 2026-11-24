package io.github.slavikjunior.syncclip.server.feature.notifications.domain

import io.github.slavikjunior.syncclip.server.contracts.error.ErrorCode
import io.github.slavikjunior.syncclip.server.contracts.error.ForbiddenException
import io.github.slavikjunior.syncclip.server.contracts.error.NotFoundException
import io.github.slavikjunior.syncclip.server.contracts.model.AuthContext
import io.github.slavikjunior.syncclip.server.contracts.util.UuidV7
import io.github.slavikjunior.syncclip.server.contracts.validation.Validators
import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.findDeviceById
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.upsertFcmToken
import io.github.slavikjunior.syncclip.server.core.database.tx
import java.time.Instant

/** Регистрация FCM-токена: deviceId строго из JWT, upsert по device_id и token (§11.1). */
class FcmRegistrationService(
    private val databaseFactory: DatabaseFactory,
    private val clock: () -> Instant = Instant::now,
) {

    suspend fun register(context: AuthContext, deviceId: String, fcmToken: String) {
        // Гость не получает push аккаунта (SEC-03).
        if (context.isGuest) throw ForbiddenException(ErrorCode.FORBIDDEN_FOR_GUEST)
        val normalizedId = Validators.uuid(deviceId, "deviceId")
        if (normalizedId != context.deviceId) {
            throw ForbiddenException(ErrorCode.DEVICE_MISMATCH)
        }
        val token = Validators.fcmToken(fcmToken)
        tx(databaseFactory.database) {
            findDeviceById(context.userId, context.deviceId) ?: throw NotFoundException(ErrorCode.DEVICE_NOT_FOUND)
            upsertFcmToken(
                id = UuidV7.newString(),
                userId = context.userId,
                deviceId = context.deviceId,
                token = token,
                now = clock(),
            )
        }
    }
}
