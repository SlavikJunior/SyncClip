package io.github.slavikjunior.syncclip.server.core.database.repository

import io.github.slavikjunior.syncclip.server.core.database.model.GuestInviteRecord
import io.github.slavikjunior.syncclip.server.core.database.tables.GuestInvitesTable
import io.github.slavikjunior.syncclip.server.core.database.tables.toInstantUtc
import io.github.slavikjunior.syncclip.server.core.database.tables.toOffsetDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import kotlin.uuid.Uuid


internal fun JdbcTransaction.insertGuestInvite(
    id: String,
    userId: String,
    createdByDeviceId: String,
    tokenHash: String,
    createdAt: Instant,
    expiresAt: Instant,
) {
    GuestInvitesTable.insert {
        it[GuestInvitesTable.id] = Uuid.parse(id)
        it[GuestInvitesTable.userId] = Uuid.parse(userId)
        it[GuestInvitesTable.createdByDeviceId] = Uuid.parse(createdByDeviceId)
        it[GuestInvitesTable.tokenHash] = tokenHash
        it[GuestInvitesTable.createdAt] = createdAt.toOffsetDateTime()
        it[GuestInvitesTable.expiresAt] = expiresAt.toOffsetDateTime()
    }
}

internal fun JdbcTransaction.findGuestInviteByTokenHash(tokenHash: String): GuestInviteRecord? =
    GuestInvitesTable.selectAll()
        .where { GuestInvitesTable.tokenHash eq tokenHash }
        .limit(1)
        .map(::toRecord)
        .firstOrNull()

/** Атомарное «сжигание»: `UPDATE … WHERE used_at IS NULL`, 0 строк — уже использован. */
internal fun JdbcTransaction.markGuestInviteUsed(id: String, usedByDeviceId: String, at: Instant = Instant.now()): Boolean =
    GuestInvitesTable.update({
        (GuestInvitesTable.id eq Uuid.parse(id)) and (GuestInvitesTable.usedAt.isNull())
    }) {
        it[usedAt] = at.toOffsetDateTime()
        it[GuestInvitesTable.usedByDeviceId] = Uuid.parse(usedByDeviceId)
    } > 0

/** Батч-очистка просроченных приглашений (OPS-08/PERF-05). */
internal fun JdbcTransaction.deleteStaleGuestInvites(before: Instant, limit: Int = 1_000): Int {
    val victims = GuestInvitesTable.selectAll()
        .where { GuestInvitesTable.expiresAt less before.toOffsetDateTime() }
        .limit(limit)
        .map { it[GuestInvitesTable.id] }
    if (victims.isEmpty()) return 0
    return GuestInvitesTable.deleteWhere { GuestInvitesTable.id inList victims }
}

private fun toRecord(row: ResultRow): GuestInviteRecord = GuestInviteRecord(
    id = row[GuestInvitesTable.id].toString(),
    userId = row[GuestInvitesTable.userId].toString(),
    createdByDeviceId = row[GuestInvitesTable.createdByDeviceId].toString(),
    tokenHash = row[GuestInvitesTable.tokenHash].trim(),
    createdAt = row[GuestInvitesTable.createdAt].toInstantUtc(),
    expiresAt = row[GuestInvitesTable.expiresAt].toInstantUtc(),
    usedAt = row[GuestInvitesTable.usedAt]?.toInstantUtc(),
    usedByDeviceId = row[GuestInvitesTable.usedByDeviceId]?.toString(),
    revokedAt = row[GuestInvitesTable.revokedAt]?.toInstantUtc(),
)
