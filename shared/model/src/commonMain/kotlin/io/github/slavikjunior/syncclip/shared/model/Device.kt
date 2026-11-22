package io.github.slavikjunior.syncclip.shared.model

/** Контракт устройства, общий для настроек и транспорта. */
data class Device(
    val id: String,
    val name: String,
    val icon: DeviceIcon,
    val platform: DevicePlatform,
    val isCurrent: Boolean = false,
)
