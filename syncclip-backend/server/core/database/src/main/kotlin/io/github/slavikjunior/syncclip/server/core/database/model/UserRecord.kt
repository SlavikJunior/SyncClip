package io.github.slavikjunior.syncclip.server.core.database.model

import java.time.Instant

data class UserRecord(
    val id: String,
    val email: String,
    val passwordHash: String,
    val passwordAlgo: String,
    val plan: String,
    val createdAt: Instant,
    val firebaseUid: String? = null,
)
