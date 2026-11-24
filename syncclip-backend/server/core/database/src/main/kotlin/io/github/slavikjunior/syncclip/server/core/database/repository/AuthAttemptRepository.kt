package io.github.slavikjunior.syncclip.server.core.database.repository

import io.github.slavikjunior.syncclip.server.core.database.model.AttemptRecord
import io.github.slavikjunior.syncclip.server.core.database.tables.AuthAttemptsTable
import io.github.slavikjunior.syncclip.server.core.database.tables.toInstantUtc
import io.github.slavikjunior.syncclip.server.core.database.tables.toOffsetDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.Instant
import kotlin.uuid.Uuid


/**
 * Журнал попыток входа. `ip_address` — типизированный `inet` (LOW-13),
 * `Validators.ipOrNull` гарантирует литерал до вызова.
 */
internal fun JdbcTransaction.insertAuthAttempt(
    id: String,
    userId: String?,
    deviceId: String?,
    emailHash: String?,
    ipAddress: String?,
    userAgent: String?,
    deviceModel: String?,
    kind: String,
    success: Boolean,
    at: Instant = Instant.now(),
) {
    AuthAttemptsTable.insert {
        it[AuthAttemptsTable.id] = Uuid.parse(id)
        it[AuthAttemptsTable.userId] = userId?.let(Uuid::parse)
        it[AuthAttemptsTable.deviceId] = deviceId?.let(Uuid::parse)
        it[AuthAttemptsTable.emailHash] = emailHash
        it[AuthAttemptsTable.ipAddress] = ipAddress
        it[AuthAttemptsTable.userAgent] = userAgent?.take(512)
        it[AuthAttemptsTable.deviceModel] = deviceModel?.take(255)
        it[AuthAttemptsTable.kind] = kind
        it[AuthAttemptsTable.success] = success
        it[attemptedAt] = at.toOffsetDateTime()
    }
}

internal fun JdbcTransaction.countFailedAttemptsSince(userId: String, kind: String, since: Instant): Int =
    AuthAttemptsTable.selectAll()
        .where {
            (AuthAttemptsTable.userId eq Uuid.parse(userId)) and
                (AuthAttemptsTable.kind eq kind) and
                (AuthAttemptsTable.success eq false) and
                (AuthAttemptsTable.attemptedAt greaterEq since.toOffsetDateTime())
        }
        .count()
        .toInt()

/**
 * Keyset-пагинация ленты: строго старше курсора `(attemptedAt, id)`.
 * Сравнение `>=` повторяло последний элемент страницы (FUNC-03).
 * `notBefore` ограничивает retention на первой странице.
 */
internal fun JdbcTransaction.listFailedAttempts(
    userId: String,
    kinds: List<String>,
    notBefore: Instant,
    before: Instant,
    beforeId: String?,
    limit: Int,
): List<AttemptRecord> {
    val at = before.toOffsetDateTime()
    val cursorCondition = if (beforeId == null) {
        AuthAttemptsTable.attemptedAt less at
    } else {
        (AuthAttemptsTable.attemptedAt less at) or
            ((AuthAttemptsTable.attemptedAt eq at) and (AuthAttemptsTable.id less Uuid.parse(beforeId)))
    }
    return AuthAttemptsTable.selectAll()
        .where {
            (AuthAttemptsTable.userId eq Uuid.parse(userId)) and
                (AuthAttemptsTable.kind inList kinds) and
                (AuthAttemptsTable.success eq false) and
                (AuthAttemptsTable.attemptedAt greaterEq notBefore.toOffsetDateTime()) and
                cursorCondition
        }
        .orderBy(
            AuthAttemptsTable.attemptedAt to SortOrder.DESC,
            AuthAttemptsTable.id to SortOrder.DESC,
        )
        .limit(limit)
        .map(::toRecord)
}

/** Время последней неудачи — для retryAfter login-lockout (SEC-06). */
internal fun JdbcTransaction.lastFailedAttemptAt(userId: String, kind: String, since: Instant): Instant? =
    AuthAttemptsTable.selectAll()
        .where {
            (AuthAttemptsTable.userId eq Uuid.parse(userId)) and
                (AuthAttemptsTable.kind eq kind) and
                (AuthAttemptsTable.success eq false) and
                (AuthAttemptsTable.attemptedAt greaterEq since.toOffsetDateTime())
        }
        .orderBy(AuthAttemptsTable.attemptedAt to SortOrder.DESC)
        .limit(1)
        .map { it[AuthAttemptsTable.attemptedAt].toInstantUtc() }
        .firstOrNull()

/** Успешный вход сбрасывает окно подсчёта неудач (SEC-06). */
internal fun JdbcTransaction.lastSuccessfulAttemptAt(userId: String, kind: String, since: Instant): Instant? =
    AuthAttemptsTable.selectAll()
        .where {
            (AuthAttemptsTable.userId eq Uuid.parse(userId)) and
                (AuthAttemptsTable.kind eq kind) and
                (AuthAttemptsTable.success eq true) and
                (AuthAttemptsTable.attemptedAt greaterEq since.toOffsetDateTime())
        }
        .orderBy(AuthAttemptsTable.attemptedAt to SortOrder.DESC)
        .limit(1)
        .map { it[AuthAttemptsTable.attemptedAt].toInstantUtc() }
        .firstOrNull()

internal fun JdbcTransaction.deleteAttemptsOlderThan(threshold: Instant, limit: Int = 5_000): Int {
    val victims = AuthAttemptsTable.selectAll()
        .where { AuthAttemptsTable.attemptedAt less threshold.toOffsetDateTime() }
        .limit(limit)
        .map { it[AuthAttemptsTable.id] }
    if (victims.isEmpty()) return 0
    return AuthAttemptsTable.deleteWhere { AuthAttemptsTable.id inList victims }
}

private fun toRecord(row: ResultRow): AttemptRecord = AttemptRecord(
    id = row[AuthAttemptsTable.id].toString(),
    userId = row[AuthAttemptsTable.userId]?.toString(),
    deviceId = row[AuthAttemptsTable.deviceId]?.toString(),
    emailHash = row[AuthAttemptsTable.emailHash]?.trim(),
    ipAddress = row[AuthAttemptsTable.ipAddress],
    userAgent = row[AuthAttemptsTable.userAgent],
    deviceModel = row[AuthAttemptsTable.deviceModel],
    kind = row[AuthAttemptsTable.kind],
    success = row[AuthAttemptsTable.success],
    attemptedAt = row[AuthAttemptsTable.attemptedAt].toInstantUtc(),
)
