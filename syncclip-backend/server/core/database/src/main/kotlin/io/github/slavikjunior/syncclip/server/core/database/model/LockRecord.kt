package io.github.slavikjunior.syncclip.server.core.database.model

import java.time.Instant

data class LockRecord(
    val userId: String,
    val failedInGroup: Int,
    val lockoutCount: Int,
    val lockedUntil: Instant?,
)
