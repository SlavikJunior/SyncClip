package io.github.slavikjunior.syncclip.server.feature.devices.api

import io.github.slavikjunior.syncclip.server.contracts.dto.device.DeviceDto
import io.github.slavikjunior.syncclip.server.core.database.model.DeviceRecord

/** Маппинг доменной записи в контракт API_SPEC `Device`. */
object DeviceDtoMapping {
    fun toDto(record: DeviceRecord, isOnline: Boolean): DeviceDto = DeviceDto(
        deviceId = record.id,
        name = record.name,
        osType = record.osType,
        icon = record.icon,
        deviceType = record.deviceType,
        createdAt = record.createdAt.toString(),
        lastSeen = record.lastSeenAt.toString(),
        isOnline = isOnline,
        expiresAt = record.expiresAt?.toString(),
    )
}
