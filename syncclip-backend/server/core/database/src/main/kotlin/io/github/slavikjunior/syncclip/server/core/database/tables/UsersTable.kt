package io.github.slavikjunior.syncclip.server.core.database.tables

import org.jetbrains.exposed.v1.core.Table

internal object UsersTable : Table("users") {
    val id = uuid("id")
    val email = varchar("email", 255)
    val passwordHash = text("password_hash")
    val passwordAlgo = varchar("password_algo", 32).default("argon2id")
    val firebaseUid = varchar("firebase_uid", 128).nullable()
    val plan = varchar("plan", 16).default("free")
    val createdAt = timestampUtc("created_at").clientDefault { java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC) }
    val updatedAt = timestampUtc("updated_at").clientDefault { java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC) }

    override val primaryKey = PrimaryKey(id)
}
