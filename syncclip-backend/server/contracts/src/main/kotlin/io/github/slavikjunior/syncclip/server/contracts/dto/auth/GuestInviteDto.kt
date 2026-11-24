package io.github.slavikjunior.syncclip.server.contracts.dto.auth

import kotlinx.serialization.Serializable

/** CG-02: приглашение гостевого устройства (TTL 10 мин). */
@Serializable
data class GuestInviteResponse(
    val inviteToken: String,
    val expiresAt: String,
    val shareUri: String,
)
