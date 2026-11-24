package io.github.slavikjunior.syncclip.server.core.database.tables

import org.jetbrains.exposed.v1.core.Table

internal object OfflineQueueTable : Table("offline_queue") {
    val id = uuid("id")

    /** BIGINT IDENTITY: порядок вставки, вытеснение и пагинация — по seq. */
    val seq = long("seq")
    val userId = uuid("user_id")
    val targetDeviceId = uuid("target_device_id")
    val eventId = uuid("event_id")
    val originDeviceId = uuid("origin_device_id")
    val encryptedPayload = binary("encrypted_payload")
    val payloadHash = varchar("payload_hash", 64)
    val clientTimestamp = long("client_timestamp")
    val createdAt = timestampUtc("created_at")
    val expiresAt = timestampUtc("expires_at")

    override val primaryKey = PrimaryKey(id)
}
