package io.github.slavikjunior.syncclip.server.core.database.tables

import org.jetbrains.exposed.v1.core.Table

internal object GuestInvitesTable : Table("guest_invites") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val createdByDeviceId = uuid("created_by_device_id")
    val tokenHash = varchar("token_hash", 64)
    val createdAt = timestampUtc("created_at")
    val expiresAt = timestampUtc("expires_at")
    val usedAt = timestampUtc("used_at").nullable()
    val usedByDeviceId = uuid("used_by_device_id").nullable()
    val revokedAt = timestampUtc("revoked_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
