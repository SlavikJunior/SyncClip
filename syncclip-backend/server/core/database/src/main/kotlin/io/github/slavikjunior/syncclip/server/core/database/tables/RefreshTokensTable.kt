package io.github.slavikjunior.syncclip.server.core.database.tables

import org.jetbrains.exposed.v1.core.Table

internal object RefreshTokensTable : Table("refresh_tokens") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val deviceId = uuid("device_id")
    val familyId = uuid("family_id")
    val parentId = uuid("parent_id").nullable()
    val jtiHash = varchar("jti_hash", 64)
    val issuedAt = timestampUtc("issued_at")
    val expiresAt = timestampUtc("expires_at")
    val revokedAt = timestampUtc("revoked_at").nullable()
    val revokeReason = varchar("revoke_reason", 32).nullable()

    override val primaryKey = PrimaryKey(id)
}
