package io.github.slavikjunior.syncclip.shared.model

/**
 * Каталог устройств, доступный фичам без зависимости от `:feature:settings`.
 *
 * Реализация живёт в settings impl и собирается в composition root; History и
 * Sync получают только этот контракт (INV-01).
 */
public interface DeviceDirectory {
    public suspend fun devices(): List<Device>
}

/** Человекочитаемое имя устройства или null, если оно неизвестно. */
public suspend fun DeviceDirectory.deviceName(deviceId: String?): String? {
    if (deviceId == null) return null
    return devices().firstOrNull { device -> device.id == deviceId }?.name
}
