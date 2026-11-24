package io.github.slavikjunior.syncclip.server.core.database.repository

import io.github.slavikjunior.syncclip.server.core.database.internal.DbErrors
import io.github.slavikjunior.syncclip.server.core.database.tables.EncryptedCanaryTable
import io.github.slavikjunior.syncclip.server.core.database.tables.toOffsetDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.Instant
import kotlin.uuid.Uuid


internal fun JdbcTransaction.findCanary(userId: String): String? =
    EncryptedCanaryTable.selectAll()
        .where { EncryptedCanaryTable.userId eq Uuid.parse(userId) }
        .limit(1)
        .map { it[EncryptedCanaryTable.encryptedValue] }
        .firstOrNull()

/** false — canary уже установлен (409 `canary_already_set`). */
internal fun JdbcTransaction.insertCanaryOnce(userId: String, encryptedValue: String, now: Instant = Instant.now()): Boolean =
    try {
        EncryptedCanaryTable.insert {
            it[EncryptedCanaryTable.userId] = Uuid.parse(userId)
            it[EncryptedCanaryTable.encryptedValue] = encryptedValue
            it[createdAt] = now.toOffsetDateTime()
            it[updatedAt] = now.toOffsetDateTime()
        }
        true
    } catch (e: Exception) {
        if (DbErrors.isUniqueViolation(e)) false else throw e
    }

internal fun JdbcTransaction.deleteCanary(userId: String): Boolean {
    val deleted = EncryptedCanaryTable.deleteWhere {
        EncryptedCanaryTable.userId eq Uuid.parse(userId)
    }
    return deleted > 0
}
