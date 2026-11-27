package io.github.slavikjunior.syncclip.feature.settings.impl.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Устройство из `GET /v1/devices`.
 *
 * Имена полей сервера (`deviceId`, `osType`) отличаются от доменных, поэтому
 * маппинг идёт через [SerialName], а не переименованием домена (INV-07).
 */
@Serializable
internal data class DeviceDto(
    @SerialName("deviceId") val id: String,
    val name: String,
    val icon: String,
    @SerialName("osType") val platform: String,
    val deviceType: String = "permanent",
    val isOnline: Boolean = false,
    val expiresAt: String? = null,
)
