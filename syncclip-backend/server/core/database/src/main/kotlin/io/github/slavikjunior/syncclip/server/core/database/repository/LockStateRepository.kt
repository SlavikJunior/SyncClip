package io.github.slavikjunior.syncclip.server.core.database.repository

import io.github.slavikjunior.syncclip.server.core.database.model.LockRecord
import io.github.slavikjunior.syncclip.server.core.database.tables.MasterPasswordLocksTable
import io.github.slavikjunior.syncclip.server.core.database.tables.toInstantUtc
import io.github.slavikjunior.syncclip.server.core.database.tables.toOffsetDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import kotlin.uuid.Uuid


internal fun JdbcTransaction.findLockStateForUpdate(userId: String): LockRecord? =
    MasterPasswordLocksTable.selectAll()
        .where { MasterPasswordLocksTable.userId eq Uuid.parse(userId) }
        .forUpdate()
        .limit(1)
        .map {
            LockRecord(
                userId = it[MasterPasswordLocksTable.userId].toString(),
                failedInGroup = it[MasterPasswordLocksTable.failedInGroup].toInt(),
                lockoutCount = it[MasterPasswordLocksTable.lockoutCount],
                lockedUntil = it[MasterPasswordLocksTable.lockedUntil]?.toInstantUtc(),
            )
        }
        .firstOrNull()

internal fun JdbcTransaction.saveLockState(record: LockRecord, now: Instant = Instant.now()) {
    val userUuid = Uuid.parse(record.userId)
    val exists = MasterPasswordLocksTable.selectAll()
        .where { MasterPasswordLocksTable.userId eq userUuid }
        .empty()
        .not()
    if (!exists) {
        MasterPasswordLocksTable.insert {
            it[userId] = userUuid
            it[failedInGroup] = record.failedInGroup.toShort()
            it[lockoutCount] = record.lockoutCount
            it[lockedUntil] = record.lockedUntil?.toOffsetDateTime()
            it[updatedAt] = now.toOffsetDateTime()
        }
    } else {
        MasterPasswordLocksTable.update({ MasterPasswordLocksTable.userId eq userUuid }) {
            it[failedInGroup] = record.failedInGroup.toShort()
            it[lockoutCount] = record.lockoutCount
            it[lockedUntil] = record.lockedUntil?.toOffsetDateTime()
            it[updatedAt] = now.toOffsetDateTime()
        }
    }
}
