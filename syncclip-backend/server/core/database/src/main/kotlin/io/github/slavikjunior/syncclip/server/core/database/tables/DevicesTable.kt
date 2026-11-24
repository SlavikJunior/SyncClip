package io.github.slavikjunior.syncclip.server.core.database.tables

import org.jetbrains.exposed.v1.core.Table

internal object DevicesTable : Table("devices") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val name = varchar("name", 255)
    val osType = varchar("os_type", 20)
    val icon = varchar("icon", 16)
    val deviceType = varchar("device_type", 16)
    val tokenVersion = integer("token_version").default(0)
    val createdAt = timestampUtc("created_at").clientDefault { java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC) }
    val lastSeenAt = timestampUtc("last_seen_at").clientDefault { java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC) }
    val expiresAt = timestampUtc("expires_at").nullable()

    override val primaryKey = PrimaryKey(userId, id)
}
