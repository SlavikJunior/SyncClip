package io.github.slavikjunior.syncclip.server.core.database.repository

import io.github.slavikjunior.syncclip.server.core.database.model.RefreshTokenRecord
import io.github.slavikjunior.syncclip.server.core.database.tables.RefreshTokensTable
import io.github.slavikjunior.syncclip.server.core.database.tables.toInstantUtc
import io.github.slavikjunior.syncclip.server.core.database.tables.toOffsetDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import kotlin.uuid.Uuid


internal fun JdbcTransaction.insertRefreshToken(
    id: String,
    userId: String,
    deviceId: String,
    familyId: String,
    parentId: String?,
    jtiHash: String,
    issuedAt: Instant,
    expiresAt: Instant,
) {
    RefreshTokensTable.insert {
        it[RefreshTokensTable.id] = Uuid.parse(id)
        it[RefreshTokensTable.userId] = Uuid.parse(userId)
        it[RefreshTokensTable.deviceId] = Uuid.parse(deviceId)
        it[RefreshTokensTable.familyId] = Uuid.parse(familyId)
        it[RefreshTokensTable.parentId] = parentId?.let(Uuid::parse)
        it[RefreshTokensTable.jtiHash] = jtiHash
        it[RefreshTokensTable.issuedAt] = issuedAt.toOffsetDateTime()
        it[RefreshTokensTable.expiresAt] = expiresAt.toOffsetDateTime()
    }
}

internal fun JdbcTransaction.findRefreshTokenByJtiHash(jtiHash: String): RefreshTokenRecord? =
    RefreshTokensTable.selectAll()
        .where { RefreshTokensTable.jtiHash eq jtiHash }
        .limit(1)
        .map(::toRecord)
        .firstOrNull()

/**
 * Читает строку под блокировкой: параллельные refresh одного токена
 * сериализуются и второй видит уже ротированное состояние (SEC-04).
 */
internal fun JdbcTransaction.findRefreshTokenByJtiHashForUpdate(jtiHash: String): RefreshTokenRecord? =
    RefreshTokensTable.selectAll()
        .where { RefreshTokensTable.jtiHash eq jtiHash }
        .forUpdate()
        .limit(1)
        .map(::toRecord)
        .firstOrNull()

internal fun JdbcTransaction.findActiveRefreshChild(parentId: String): RefreshTokenRecord? =
    RefreshTokensTable.selectAll()
        .where {
            (RefreshTokensTable.parentId eq Uuid.parse(parentId)) and
                (RefreshTokensTable.revokedAt.isNull())
        }
        .orderBy(RefreshTokensTable.issuedAt to SortOrder.DESC)
        .limit(1)
        .map(::toRecord)
        .firstOrNull()

internal fun JdbcTransaction.revokeRefreshToken(id: String, reason: String, at: Instant = Instant.now()) {
    RefreshTokensTable.update({
        (RefreshTokensTable.id eq Uuid.parse(id)) and (RefreshTokensTable.revokedAt.isNull())
    }) {
        it[revokedAt] = at.toOffsetDateTime()
        it[revokeReason] = reason
    }
}

internal fun JdbcTransaction.revokeRefreshFamily(familyId: String, reason: String, at: Instant = Instant.now()): Int =
    RefreshTokensTable.update({
        (RefreshTokensTable.familyId eq Uuid.parse(familyId)) and (RefreshTokensTable.revokedAt.isNull())
    }) {
        it[revokedAt] = at.toOffsetDateTime()
        it[revokeReason] = reason
    }

internal fun JdbcTransaction.revokeActiveRefreshForDevice(
    userId: String,
    deviceId: String,
    reason: String,
    at: Instant = Instant.now(),
): Int =
    RefreshTokensTable.update({
        (RefreshTokensTable.userId eq Uuid.parse(userId)) and
            (RefreshTokensTable.deviceId eq Uuid.parse(deviceId)) and
            (RefreshTokensTable.revokedAt.isNull())
    }) {
        it[revokedAt] = at.toOffsetDateTime()
        it[revokeReason] = reason
    }

internal fun JdbcTransaction.revokeActiveRefreshForUser(userId: String, reason: String, at: Instant = Instant.now()): Int =
    RefreshTokensTable.update({
        (RefreshTokensTable.userId eq Uuid.parse(userId)) and (RefreshTokensTable.revokedAt.isNull())
    }) {
        it[revokedAt] = at.toOffsetDateTime()
        it[revokeReason] = reason
    }

internal fun JdbcTransaction.deleteExpiredRefreshTokens(now: Instant = Instant.now(), limit: Int = 1_000): Int {
    val victims = RefreshTokensTable.selectAll()
        .where { RefreshTokensTable.expiresAt less now.toOffsetDateTime() }
        .limit(limit)
        .map { it[RefreshTokensTable.id] }
    if (victims.isEmpty()) return 0
    return RefreshTokensTable.deleteWhere { RefreshTokensTable.id inList victims }
}

/**
 * Отозванные строки нужны только для reuse-detection; через неделю они
 * бесполезны и превращаются в bloat (LOW-12).
 */
internal fun JdbcTransaction.deleteRevokedRefreshTokensBefore(threshold: Instant, limit: Int = 1_000): Int {
    val victims = RefreshTokensTable.selectAll()
        .where {
            (RefreshTokensTable.revokedAt.isNotNull()) and
                (RefreshTokensTable.revokedAt less threshold.toOffsetDateTime())
        }
        .limit(limit)
        .map { it[RefreshTokensTable.id] }
    if (victims.isEmpty()) return 0
    return RefreshTokensTable.deleteWhere { RefreshTokensTable.id inList victims }
}

private fun toRecord(row: ResultRow): RefreshTokenRecord = RefreshTokenRecord(
    id = row[RefreshTokensTable.id].toString(),
    userId = row[RefreshTokensTable.userId].toString(),
    deviceId = row[RefreshTokensTable.deviceId].toString(),
    familyId = row[RefreshTokensTable.familyId].toString(),
    parentId = row[RefreshTokensTable.parentId]?.toString(),
    jtiHash = row[RefreshTokensTable.jtiHash].trim(),
    issuedAt = row[RefreshTokensTable.issuedAt].toInstantUtc(),
    expiresAt = row[RefreshTokensTable.expiresAt].toInstantUtc(),
    revokedAt = row[RefreshTokensTable.revokedAt]?.toInstantUtc(),
    revokeReason = row[RefreshTokensTable.revokeReason],
)
