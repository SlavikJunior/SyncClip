package io.github.slavikjunior.syncclip.server.core.database.model

import java.time.Instant

data class AttemptRecord(
    val id: String,
    val userId: String?,
    val deviceId: String?,
    val emailHash: String?,
    val ipAddress: String?,
    val userAgent: String?,
    val deviceModel: String?,
    val kind: String,
    val success: Boolean,
    val attemptedAt: Instant,
)
