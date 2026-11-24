package io.github.slavikjunior.syncclip.server.contracts.dto.auth

import kotlinx.serialization.Serializable

/** CG-08: `refreshToken` опционален — при отсутствии гасятся все токены устройства. */
@Serializable
data class LogoutRequest(
    val refreshToken: String? = null,
    val allDevices: Boolean = false,
)
