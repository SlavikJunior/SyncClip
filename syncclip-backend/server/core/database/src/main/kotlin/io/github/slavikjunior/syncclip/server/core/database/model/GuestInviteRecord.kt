package io.github.slavikjunior.syncclip.server.core.database.model

import java.time.Instant

data class GuestInviteRecord(
    val id: String,
    val userId: String,
    val createdByDeviceId: String,
    val tokenHash: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val usedAt: Instant?,
    val usedByDeviceId: String?,
    val revokedAt: Instant?,
)
