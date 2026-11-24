package io.github.slavikjunior.syncclip.server.contracts.dto.auth

import kotlinx.serialization.Serializable

@Serializable
data class GuestExchangeRequest(
    val inviteToken: String,
    val deviceId: String,
    val deviceName: String,
    val osType: String,
)

/** Гость получает только access (без refresh, FR-1.6). */
@Serializable
data class GuestExchangeResponse(
    val userId: String,
    val deviceId: String,
    val accessToken: String,
    val expiresAt: String,
)
