package io.github.slavikjunior.syncclip.server.core.database.repository

import io.github.slavikjunior.syncclip.server.core.database.internal.DbErrors
import io.github.slavikjunior.syncclip.server.core.database.model.FcmTokenRecord
import io.github.slavikjunior.syncclip.server.core.database.tables.FcmTokensTable
import io.github.slavikjunior.syncclip.server.core.database.tables.toInstantUtc
import io.github.slavikjunior.syncclip.server.core.database.tables.toOffsetDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import kotlin.uuid.Uuid


internal fun JdbcTransaction.upsertFcmToken(
    id: String,
    userId: String,
    deviceId: String,
    token: String,
    now: Instant = Instant.now(),
) {
    try {
        upsertFcmTokenOnce(id, userId, deviceId, token, now)
    } catch (e: Exception) {
        // Конкурентная регистрация того же токена: чистим конфликт и повторяем
        // один раз, иначе гонка превращается в 500 (FUNC-06).
        if (!DbErrors.isUniqueViolation(e)) throw e
        FcmTokensTable.deleteWhere { FcmTokensTable.token eq token }
        upsertFcmTokenOnce(id, userId, deviceId, token, now)
    }
}

private fun JdbcTransaction.upsertFcmTokenOnce(
    id: String,
    userId: String,
    deviceId: String,
    token: String,
    now: Instant,
) {
    val userUuid = Uuid.parse(userId)
    val deviceUuid = Uuid.parse(deviceId)
    // Переустановка: тот же токен мог остаться за другим deviceId.
    FcmTokensTable.deleteWhere {
        (FcmTokensTable.token eq token) and (FcmTokensTable.deviceId neq deviceUuid)
    }
    val existing = FcmTokensTable.selectAll()
        .where {
            (FcmTokensTable.userId eq userUuid) and (FcmTokensTable.deviceId eq deviceUuid)
        }
        .limit(1)
        .map(::toRecord)
        .firstOrNull()
    if (existing != null) {
        FcmTokensTable.update({ FcmTokensTable.id eq Uuid.parse(existing.id) }) {
            it[FcmTokensTable.token] = token
            it[FcmTokensTable.userId] = userUuid
            it[updatedAt] = now.toOffsetDateTime()
        }
    } else {
        FcmTokensTable.insert {
            it[FcmTokensTable.id] = Uuid.parse(id)
            it[FcmTokensTable.userId] = userUuid
            it[FcmTokensTable.deviceId] = deviceUuid
            it[FcmTokensTable.token] = token
            it[createdAt] = now.toOffsetDateTime()
            it[updatedAt] = now.toOffsetDateTime()
        }
    }
}

/**
 * Токены только **постоянных** устройств: гость не должен получать
 * security-события аккаунта (SEC-03/LOW-11).
 */
internal fun JdbcTransaction.listFcmTokensForUser(userId: String, exceptDeviceId: String? = null): List<String> {
    val permanent = listPermanentDeviceIds(userId)
    if (permanent.isEmpty()) return emptyList()
    val allowed = permanent.map(Uuid::parse)
    val excluded = exceptDeviceId?.let(Uuid::parse)
    return FcmTokensTable.selectAll()
        .where {
            val base = (FcmTokensTable.userId eq Uuid.parse(userId)) and
                (FcmTokensTable.deviceId inList allowed)
            if (excluded == null) base else base and (FcmTokensTable.deviceId neq excluded)
        }
        .map { it[FcmTokensTable.token] }
}

internal fun JdbcTransaction.deleteFcmTokenByToken(token: String): Int =
    FcmTokensTable.deleteWhere { FcmTokensTable.token eq token }

private fun toRecord(row: ResultRow): FcmTokenRecord = FcmTokenRecord(
    id = row[FcmTokensTable.id].toString(),
    userId = row[FcmTokensTable.userId].toString(),
    deviceId = row[FcmTokensTable.deviceId].toString(),
    token = row[FcmTokensTable.token],
    createdAt = row[FcmTokensTable.createdAt].toInstantUtc(),
    updatedAt = row[FcmTokensTable.updatedAt].toInstantUtc(),
)
