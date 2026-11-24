package io.github.slavikjunior.syncclip.server.core.database.tables

import org.jetbrains.exposed.v1.core.Table

internal object MasterPasswordLocksTable : Table("master_password_locks") {
    val userId = uuid("user_id")
    val failedInGroup = short("failed_in_group").default(0)
    val lockoutCount = integer("lockout_count").default(0)
    val lockedUntil = timestampUtc("locked_until").nullable()
    val updatedAt = timestampUtc("updated_at").clientDefault { java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC) }

    override val primaryKey = PrimaryKey(userId)
}
