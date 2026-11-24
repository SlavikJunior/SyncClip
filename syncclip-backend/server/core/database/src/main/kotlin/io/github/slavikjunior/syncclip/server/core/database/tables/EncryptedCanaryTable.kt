package io.github.slavikjunior.syncclip.server.core.database.tables

import org.jetbrains.exposed.v1.core.Table

internal object EncryptedCanaryTable : Table("encrypted_canary") {
    val userId = uuid("user_id")
    val encryptedValue = text("encrypted_value")
    val createdAt = timestampUtc("created_at").clientDefault { java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC) }
    val updatedAt = timestampUtc("updated_at").clientDefault { java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC) }

    override val primaryKey = PrimaryKey(userId)
}
