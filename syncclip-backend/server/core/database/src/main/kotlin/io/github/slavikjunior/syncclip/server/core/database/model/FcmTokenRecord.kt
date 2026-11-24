package io.github.slavikjunior.syncclip.server.core.database.model

import java.time.Instant

data class FcmTokenRecord(
    val id: String,
    val userId: String,
    val deviceId: String,
    val token: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
