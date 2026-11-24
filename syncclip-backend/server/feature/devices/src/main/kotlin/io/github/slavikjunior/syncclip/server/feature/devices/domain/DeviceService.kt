package io.github.slavikjunior.syncclip.server.feature.devices.domain

import io.github.slavikjunior.syncclip.server.contracts.dto.device.DeviceDto
import io.github.slavikjunior.syncclip.server.contracts.error.ErrorCode
import io.github.slavikjunior.syncclip.server.contracts.error.ForbiddenException
import io.github.slavikjunior.syncclip.server.contracts.error.NotFoundException
import io.github.slavikjunior.syncclip.server.contracts.model.AuthContext
import io.github.slavikjunior.syncclip.server.contracts.port.UserSessionGateway
import io.github.slavikjunior.syncclip.server.contracts.validation.Validators
import io.github.slavikjunior.syncclip.server.contracts.ws.RevocationReason
import io.github.slavikjunior.syncclip.server.contracts.ws.ServerWsMessage
import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.github.slavikjunior.syncclip.server.core.database.model.DeviceRecord
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.deleteDevice
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.deleteQueuedPacketsByTarget
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.findDeviceById
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.listDevicesByUser
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.updateDeviceProfile
import io.github.slavikjunior.syncclip.server.core.database.tx
import io.github.slavikjunior.syncclip.server.feature.devices.api.DeviceDtoMapping
import java.time.Instant

/** Управление устройствами (FR-6.1) + broadcast DEVICE_UPDATED/REMOVED (CG-03). */
class DeviceService(
    private val databaseFactory: DatabaseFactory,
    private val sessionGateway: UserSessionGateway,
    private val clock: () -> Instant = Instant::now,
) {

    suspend fun list(context: AuthContext): List<DeviceDto> {
        requirePermanent(context)
        val devices = tx(databaseFactory.database) { listDevicesByUser(context.userId) }
        return devices.map { DeviceDtoMapping.toDto(it, sessionGateway.isOnline(context.userId, it.id)) }
    }

    suspend fun update(context: AuthContext, deviceId: String, name: String?, icon: String?): DeviceDto {
        requirePermanent(context)
        val normalizedName = name?.let(Validators::patchDeviceName)
        val normalizedIcon = icon?.let(Validators::icon)?.wire
        if (normalizedName == null && normalizedIcon == null) {
            throw io.github.slavikjunior.syncclip.server.contracts.error.ValidationException(
                "body",
                "empty_update",
            )
        }
        val record: DeviceRecord = tx(databaseFactory.database) {
            updateDeviceProfile(
                userId = context.userId,
                deviceId = deviceId,
                name = normalizedName,
                icon = normalizedIcon,
                now = clock(),
            ) ?: throw NotFoundException(ErrorCode.DEVICE_NOT_FOUND)
        }
        val dto = DeviceDtoMapping.toDto(record, sessionGateway.isOnline(context.userId, record.id))
        sessionGateway.broadcast(
            userId = context.userId,
            message = ServerWsMessage.DeviceUpdated(dto),
        )
        return dto
    }

    suspend fun delete(context: AuthContext, deviceId: String) {
        requirePermanent(context)
        val target = tx(databaseFactory.database) {
            val device = findDeviceById(context.userId, deviceId)
            if (device == null || device.userId != context.userId) {
                throw NotFoundException(ErrorCode.DEVICE_NOT_FOUND)
            }
            // Явное удаление очереди, чтобы метрика была точной (§10.4), затем каскад FK.
            deleteQueuedPacketsByTarget(context.userId, deviceId)
            deleteDevice(context.userId, deviceId)
            device
        }
        // Удаляемому устройству — SESSION_REVOKED(device_removed), остальным — DEVICE_REMOVED.
        sessionGateway.revokeDevice(context.userId, target.id, RevocationReason.DEVICE_REMOVED)
        sessionGateway.broadcast(
            userId = context.userId,
            message = ServerWsMessage.DeviceRemoved(target.id),
            exceptDeviceId = target.id,
        )
    }

    private fun requirePermanent(context: AuthContext) {
        if (context.isGuest) throw ForbiddenException(ErrorCode.FORBIDDEN_FOR_GUEST)
    }
}
