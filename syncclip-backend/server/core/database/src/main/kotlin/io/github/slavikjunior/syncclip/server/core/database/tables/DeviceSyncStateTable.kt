package io.github.slavikjunior.syncclip.server.core.database.tables

import org.jetbrains.exposed.v1.core.Table

/** Watermark последнего ACK на устройстве в разрезе пользователя (FUNC-05/SEC-09). */
internal object DeviceSyncStateTable : Table("device_sync_state") {
    val userId = uuid("user_id")
    val deviceId = uuid("device_id")
    val lastAckedEventId = uuid("last_acked_event_id").nullable()
    val lastAckedSeq = long("last_acked_seq").nullable()
    val updatedAt = timestampUtc("updated_at").clientDefault {
        java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
    }

    override val primaryKey = PrimaryKey(userId, deviceId)
}
