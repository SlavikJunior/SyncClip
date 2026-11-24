package io.github.slavikjunior.syncclip.server.core.database.tables

import org.jetbrains.exposed.v1.core.Table

internal object FcmTokensTable : Table("fcm_tokens") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val deviceId = uuid("device_id")
    val token = text("token")
    val createdAt = timestampUtc("created_at").clientDefault { java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC) }
    val updatedAt = timestampUtc("updated_at").clientDefault { java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC) }

    override val primaryKey = PrimaryKey(id)
}
