package io.github.slavikjunior.syncclip.server.core.database.repository

import io.github.slavikjunior.syncclip.server.core.database.model.QueuedPacketRecord
import io.github.slavikjunior.syncclip.server.core.database.tables.OfflineQueueTable
import io.github.slavikjunior.syncclip.server.core.database.tables.toInstantUtc
import io.github.slavikjunior.syncclip.server.core.database.tables.toOffsetDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.Instant
import kotlin.uuid.Uuid


/** `ON CONFLICT (target_device_id, event_id) DO NOTHING`; false — дубликат. */
internal fun JdbcTransaction.insertQueuedPacketIgnore(
    id: String,
    userId: String,
    targetDeviceId: String,
    eventId: String,
    originDeviceId: String,
    encryptedPayload: ByteArray,
    payloadHash: String,
    clientTimestamp: Long,
    createdAt: Instant,
    expiresAt: Instant,
): Boolean {
    val statement = OfflineQueueTable.insertIgnore {
        it[OfflineQueueTable.id] = Uuid.parse(id)
        it[OfflineQueueTable.userId] = Uuid.parse(userId)
        it[OfflineQueueTable.targetDeviceId] = Uuid.parse(targetDeviceId)
        it[OfflineQueueTable.eventId] = Uuid.parse(eventId)
        it[OfflineQueueTable.originDeviceId] = Uuid.parse(originDeviceId)
        it[OfflineQueueTable.encryptedPayload] = encryptedPayload
        it[OfflineQueueTable.payloadHash] = payloadHash
        it[OfflineQueueTable.clientTimestamp] = clientTimestamp
        it[OfflineQueueTable.createdAt] = createdAt.toOffsetDateTime()
        it[OfflineQueueTable.expiresAt] = expiresAt.toOffsetDateTime()
    }
    return statement.insertedCount > 0
}

internal fun JdbcTransaction.countQueuedPackets(userId: String, targetDeviceId: String): Int =
    OfflineQueueTable.selectAll()
        .where {
            (OfflineQueueTable.userId eq Uuid.parse(userId)) and
                (OfflineQueueTable.targetDeviceId eq Uuid.parse(targetDeviceId))
        }
        .count()
        .toInt()

/** Удаляет `excess` самых старых строк (по монотонному seq). */
internal fun JdbcTransaction.evictOldestQueuedPackets(userId: String, targetDeviceId: String, excess: Int): Int {
    if (excess <= 0) return 0
    val victims = OfflineQueueTable.selectAll()
        .where {
            (OfflineQueueTable.userId eq Uuid.parse(userId)) and
                (OfflineQueueTable.targetDeviceId eq Uuid.parse(targetDeviceId))
        }
        .orderBy(OfflineQueueTable.seq to SortOrder.ASC)
        .limit(excess)
        .map { it[OfflineQueueTable.id] }
    if (victims.isEmpty()) return 0
    return OfflineQueueTable.deleteWhere { OfflineQueueTable.id inList victims }
}

internal fun JdbcTransaction.findQueueAnchorSeq(userId: String, targetDeviceId: String, eventId: String): Long? =
    OfflineQueueTable.selectAll()
        .where {
            (OfflineQueueTable.userId eq Uuid.parse(userId)) and
                (OfflineQueueTable.targetDeviceId eq Uuid.parse(targetDeviceId)) and
                (OfflineQueueTable.eventId eq Uuid.parse(eventId))
        }
        .limit(1)
        .map { it[OfflineQueueTable.seq] }
        .firstOrNull()

/** Пользователь, которому принадлежит eventId в очереди (CG-23). */
internal fun JdbcTransaction.findQueueAnchorUserId(eventId: String): String? =
    OfflineQueueTable.selectAll()
        .where { OfflineQueueTable.eventId eq Uuid.parse(eventId) }
        .limit(1)
        .map { it[OfflineQueueTable.userId].toString() }
        .firstOrNull()

internal fun JdbcTransaction.listPendingPackets(
    userId: String,
    targetDeviceId: String,
    afterSeq: Long?,
    limit: Int,
): List<QueuedPacketRecord> {
    val query = OfflineQueueTable.selectAll()
        .where {
            val base = (OfflineQueueTable.userId eq Uuid.parse(userId)) and
                (OfflineQueueTable.targetDeviceId eq Uuid.parse(targetDeviceId))
            if (afterSeq == null) base else base and (OfflineQueueTable.seq greater afterSeq)
        }
        .orderBy(OfflineQueueTable.seq to SortOrder.ASC)
        .limit(limit)
    return query.map(::toRecord)
}

internal fun JdbcTransaction.countPendingPackets(userId: String, targetDeviceId: String, afterSeq: Long?): Int {
    val query = OfflineQueueTable.selectAll()
        .where {
            val base = (OfflineQueueTable.userId eq Uuid.parse(userId)) and
                (OfflineQueueTable.targetDeviceId eq Uuid.parse(targetDeviceId))
            if (afterSeq == null) base else base and (OfflineQueueTable.seq greater afterSeq)
        }
    return query.count().toInt()
}

/** ACK идемпотентен: 0 удалённых строк — норма (пакет доставлен по LAN или истёк). */
internal fun JdbcTransaction.deleteQueuedPacketByAck(userId: String, targetDeviceId: String, eventId: String): Boolean =
    OfflineQueueTable.deleteWhere {
        (OfflineQueueTable.userId eq Uuid.parse(userId)) and
            (OfflineQueueTable.targetDeviceId eq Uuid.parse(targetDeviceId)) and
            (OfflineQueueTable.eventId eq Uuid.parse(eventId))
    } > 0

internal fun JdbcTransaction.deleteQueuedPacketsByTarget(userId: String, targetDeviceId: String): Int =
    OfflineQueueTable.deleteWhere {
        (OfflineQueueTable.userId eq Uuid.parse(userId)) and
            (OfflineQueueTable.targetDeviceId eq Uuid.parse(targetDeviceId))
    }

/** TTL-очистка пачками (индекс по expires_at). */
internal fun JdbcTransaction.deleteExpiredQueuedPackets(now: Instant = Instant.now(), limit: Int = 5_000): Int {
    val victims = OfflineQueueTable.selectAll()
        .where { OfflineQueueTable.expiresAt less now.toOffsetDateTime() }
        .limit(limit)
        .map { it[OfflineQueueTable.id] }
    if (victims.isEmpty()) return 0
    return OfflineQueueTable.deleteWhere { OfflineQueueTable.id inList victims }
}

internal fun JdbcTransaction.countQueuedPacketsTotal(): Int = OfflineQueueTable.selectAll().count().toInt()

private fun toRecord(row: ResultRow): QueuedPacketRecord = QueuedPacketRecord(
    id = row[OfflineQueueTable.id].toString(),
    seq = row[OfflineQueueTable.seq],
    userId = row[OfflineQueueTable.userId].toString(),
    targetDeviceId = row[OfflineQueueTable.targetDeviceId].toString(),
    eventId = row[OfflineQueueTable.eventId].toString(),
    originDeviceId = row[OfflineQueueTable.originDeviceId].toString(),
    encryptedPayload = row[OfflineQueueTable.encryptedPayload],
    payloadHash = row[OfflineQueueTable.payloadHash].trim(),
    clientTimestamp = row[OfflineQueueTable.clientTimestamp],
    createdAt = row[OfflineQueueTable.createdAt].toInstantUtc(),
    expiresAt = row[OfflineQueueTable.expiresAt].toInstantUtc(),
)
