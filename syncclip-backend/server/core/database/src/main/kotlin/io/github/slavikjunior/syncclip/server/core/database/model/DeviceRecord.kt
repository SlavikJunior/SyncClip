package io.github.slavikjunior.syncclip.server.core.database.model

import java.time.Instant

data class DeviceRecord(
    val id: String,
    val userId: String,
    val name: String,
    val osType: String,
    val icon: String,
    val deviceType: String,
    val tokenVersion: Int,
    val createdAt: Instant,
    val lastSeenAt: Instant,
    val expiresAt: Instant?,
) {
    val isGuest: Boolean get() = deviceType == "guest"
}
