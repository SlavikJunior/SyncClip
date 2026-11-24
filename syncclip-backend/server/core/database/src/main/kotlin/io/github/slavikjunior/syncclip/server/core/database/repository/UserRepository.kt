package io.github.slavikjunior.syncclip.server.core.database.repository

import io.github.slavikjunior.syncclip.server.core.database.internal.DbErrors
import io.github.slavikjunior.syncclip.server.core.database.model.UserRecord
import io.github.slavikjunior.syncclip.server.core.database.tables.UsersTable
import io.github.slavikjunior.syncclip.server.core.database.tables.toInstantUtc
import io.github.slavikjunior.syncclip.server.core.database.tables.toOffsetDateTime
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import kotlin.uuid.Uuid


internal fun JdbcTransaction.insertUser(
    id: String,
    email: String,
    passwordHash: String,
    passwordAlgo: String,
    plan: String = "free",
    now: Instant = Instant.now(),
): Boolean {
    val rowId = Uuid.parse(id)
    val existing = UsersTable.selectAll().where { UsersTable.id eq rowId }.empty().not()
    if (existing) return false
    return try {
        UsersTable.insert {
            it[UsersTable.id] = rowId
            it[UsersTable.email] = email
            it[UsersTable.passwordHash] = passwordHash
            it[UsersTable.passwordAlgo] = passwordAlgo
            it[UsersTable.plan] = plan
            it[createdAt] = now.toOffsetDateTime()
            it[updatedAt] = now.toOffsetDateTime()
        }
        true
    } catch (e: Exception) {
        // Гонка register: уникальный индекс lower(email) — это 409, не 500 (FUNC-06).
        if (DbErrors.isUniqueViolation(e)) false else throw e
    }
}

internal fun JdbcTransaction.findUserByEmail(email: String): UserRecord? =
    UsersTable.selectAll()
        .where { UsersTable.email eq email.lowercase() }
        .limit(1)
        .map(::toRecord)
        .firstOrNull()

/** Поиск аккаунта по Firebase Auth UID (sub). */
internal fun JdbcTransaction.findUserByFirebaseUid(firebaseUid: String): UserRecord? =
    UsersTable.selectAll()
        .where { UsersTable.firebaseUid eq firebaseUid }
        .limit(1)
        .map(::toRecord)
        .firstOrNull()

/**
 * Создаёт аккаунт из verified Firebase identity. Пароль не используется:
 * marker в password_hash проходит CHECK длины и не может быть валидным PBKDF2.
 */
internal fun JdbcTransaction.insertFirebaseUser(
    id: String,
    email: String,
    firebaseUid: String,
    now: Instant,
): Boolean {
    val rowId = Uuid.parse(id)
    return try {
        UsersTable.insert {
            it[UsersTable.id] = rowId
            it[UsersTable.email] = email.lowercase()
            it[passwordHash] = "firebase-auth:$firebaseUid"
            it[passwordAlgo] = "firebase"
            it[UsersTable.firebaseUid] = firebaseUid
            it[plan] = "free"
            it[createdAt] = now.toOffsetDateTime()
            it[updatedAt] = now.toOffsetDateTime()
        }
        true
    } catch (e: Exception) {
        if (DbErrors.isUniqueViolation(e)) false else throw e
    }
}

/** Привязывает Firebase UID к существующему email-аккаунту (additive migration). */
internal fun JdbcTransaction.linkFirebaseUid(userId: String, firebaseUid: String): Boolean =
    try {
        UsersTable.update({
            (UsersTable.id eq Uuid.parse(userId)) and (UsersTable.firebaseUid.isNull())
        }) {
            it[UsersTable.firebaseUid] = firebaseUid
        } > 0
    } catch (e: Exception) {
        if (DbErrors.isUniqueViolation(e)) false else throw e
    }

/** Rehash-on-login при росте итераций/смене алгоритма (LOW-01). */
internal fun JdbcTransaction.updateUserPasswordHash(id: String, passwordHash: String, passwordAlgo: String) {
    UsersTable.update({ UsersTable.id eq Uuid.parse(id) }) {
        it[UsersTable.passwordHash] = passwordHash
        it[UsersTable.passwordAlgo] = passwordAlgo
    }
}

private fun toRecord(row: ResultRow): UserRecord = UserRecord(
    id = row[UsersTable.id].toString(),
    email = row[UsersTable.email],
    passwordHash = row[UsersTable.passwordHash],
    passwordAlgo = row[UsersTable.passwordAlgo],
    plan = row[UsersTable.plan],
    createdAt = row[UsersTable.createdAt].toInstantUtc(),
    firebaseUid = row[UsersTable.firebaseUid],
)
