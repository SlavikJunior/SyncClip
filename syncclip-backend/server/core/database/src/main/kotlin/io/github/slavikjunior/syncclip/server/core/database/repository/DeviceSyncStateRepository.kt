package io.github.slavikjunior.syncclip.server.core.database.repository

import io.github.slavikjunior.syncclip.server.core.database.model.AckWatermark
import io.github.slavikjunior.syncclip.server.core.database.tables.DeviceSyncStateTable
import io.github.slavikjunior.syncclip.server.core.database.tables.toOffsetDateTime
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.Instant
import kotlin.uuid.Uuid

internal fun JdbcTransaction.findAckWatermark(userId: String, deviceId: String): AckWatermark? =
    DeviceSyncStateTable.selectAll()
        .where {
            (DeviceSyncStateTable.userId eq Uuid.parse(userId)) and
                (DeviceSyncStateTable.deviceId eq Uuid.parse(deviceId))
        }
        .limit(1)
        .mapNotNull { row ->
            val eventId = row[DeviceSyncStateTable.lastAckedEventId] ?: return@mapNotNull null
            val seq = row[DeviceSyncStateTable.lastAckedSeq] ?: return@mapNotNull null
            AckWatermark(eventId = eventId.toString(), seq = seq)
        }
        .firstOrNull()

/**
 * Обновляет watermark только вперёд: out-of-order ACK старого пакета не должен
 * откатить курсор. `ON CONFLICT … WHERE` делает это атомарно (FUNC-05).
 */
internal fun JdbcTransaction.saveAckWatermark(
    userId: String,
    deviceId: String,
    eventId: String,
    seq: Long,
    at: Instant = Instant.now(),
) {
    exec(
        """
        INSERT INTO device_sync_state (user_id, device_id, last_acked_event_id, last_acked_seq, updated_at)
        VALUES (CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid), ?, CAST(? AS timestamptz))
        ON CONFLICT (user_id, device_id) DO UPDATE
        SET last_acked_event_id = EXCLUDED.last_acked_event_id,
            last_acked_seq = EXCLUDED.last_acked_seq,
            updated_at = EXCLUDED.updated_at
        WHERE device_sync_state.last_acked_seq IS NULL
           OR EXCLUDED.last_acked_seq >= device_sync_state.last_acked_seq
        """.trimIndent(),
        listOf(
            TextColumnType() to userId,
            TextColumnType() to deviceId,
            TextColumnType() to eventId,
            LongColumnType() to seq,
            TextColumnType() to at.toOffsetDateTime().toString(),
        ),
        StatementType.INSERT,
    )
}
