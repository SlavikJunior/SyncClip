package io.github.slavikjunior.syncclip.server.core.database.tables

import org.jetbrains.exposed.v1.core.Table

internal object AuthAttemptsTable : Table("auth_attempts") {
    val id = uuid("id")
    val userId = uuid("user_id").nullable()
    val deviceId = uuid("device_id").nullable()
    val emailHash = varchar("email_hash", 64).nullable()
    val ipAddress = registerColumn<String>("ip_address", InetColumnType).nullable()
    val userAgent = varchar("user_agent", 512).nullable()
    val deviceModel = varchar("device_model", 255).nullable()
    val kind = varchar("kind", 32)
    val success = bool("success")
    val attemptedAt = timestampUtc("attempted_at").clientDefault { java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC) }

    override val primaryKey = PrimaryKey(id)
}
