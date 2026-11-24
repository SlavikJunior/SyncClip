package io.github.slavikjunior.syncclip.server.contracts.dto.device

import kotlinx.serialization.Serializable

/** API_SPEC `Device`. */
@Serializable
data class DeviceDto(
    val deviceId: String,
    val name: String,
    val osType: String,
    val icon: String,
    val deviceType: String,
    val createdAt: String,
    val lastSeen: String,
    val isOnline: Boolean,
    val expiresAt: String? = null,
)
