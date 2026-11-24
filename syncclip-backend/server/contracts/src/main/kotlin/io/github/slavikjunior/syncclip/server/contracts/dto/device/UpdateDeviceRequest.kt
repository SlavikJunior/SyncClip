package io.github.slavikjunior.syncclip.server.contracts.dto.device

import kotlinx.serialization.Serializable

/** PATCH /devices/{id}: минимум одно поле, иначе 400 (дизайн §6.2). */
@Serializable
data class UpdateDeviceRequest(
    val name: String? = null,
    val icon: String? = null,
)
