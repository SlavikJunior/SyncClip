package io.github.slavikjunior.syncclip.server.core.database.model

import java.time.Instant

data class RefreshTokenRecord(
    val id: String,
    val userId: String,
    val deviceId: String,
    val familyId: String,
    val parentId: String?,
    val jtiHash: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant?,
    val revokeReason: String?,
) {
    val isActive: Boolean get() = revokedAt == null
}
