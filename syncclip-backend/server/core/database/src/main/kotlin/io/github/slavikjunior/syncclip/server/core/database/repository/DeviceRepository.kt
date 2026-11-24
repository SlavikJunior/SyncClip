package io.github.slavikjunior.syncclip.server.core.database.repository

import io.github.slavikjunior.syncclip.server.core.database.internal.DbErrors
import io.github.slavikjunior.syncclip.server.core.database.model.DeviceRecord
import io.github.slavikjunior.syncclip.server.core.database.model.ExpiredGuest
import io.github.slavikjunior.syncclip.server.core.database.tables.DevicesTable
import io.github.slavikjunior.syncclip.server.core.database.tables.toInstantUtc
import io.github.slavikjunior.syncclip.server.core.database.tables.toOffsetDateTime
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import kotlin.uuid.Uuid


internal fun JdbcTransaction.insertDevice(
    id: String,
    userId: String,
    name: String,
    osType: String,
    icon: String,
    deviceType: String,
    expiresAt: Instant?,
    now: Instant = Instant.now(),
): Boolean =
    try {
        DevicesTable.insert {
            it[DevicesTable.id] = Uuid.parse(id)
            it[DevicesTable.userId] = Uuid.parse(userId)
            it[DevicesTable.name] = name
            it[DevicesTable.osType] = osType
            it[DevicesTable.icon] = icon
            it[DevicesTable.deviceType] = deviceType
            it[tokenVersion] = 0
            it[createdAt] = now.toOffsetDateTime()
            it[lastSeenAt] = now.toOffsetDateTime()
            it[DevicesTable.expiresAt] = expiresAt?.toOffsetDateTime()
        }
        true
    } catch (e: Exception) {
        if (DbErrors.isUniqueViolation(e)) false else throw e
    }

internal fun JdbcTransaction.findDeviceById(userId: String, id: String): DeviceRecord? =
    DevicesTable.selectAll()
        .where {
            (DevicesTable.userId eq Uuid.parse(userId)) and
                (DevicesTable.id eq Uuid.parse(id))
        }
        .limit(1)
        .map(::toRecord)
        .firstOrNull()

internal fun JdbcTransaction.listDevicesByUser(userId: String): List<DeviceRecord> =
    DevicesTable.selectAll()
        .where { DevicesTable.userId eq Uuid.parse(userId) }
        .orderBy(DevicesTable.createdAt to org.jetbrains.exposed.v1.core.SortOrder.ASC)
        .map(::toRecord)

/** Только постоянные устройства: фильтр для FCM-рассылки (SEC-03). */
internal fun JdbcTransaction.listPermanentDeviceIds(userId: String): List<String> =
    DevicesTable.selectAll()
        .where {
            (DevicesTable.userId eq Uuid.parse(userId)) and
                (DevicesTable.deviceType eq PERMANENT_DEVICE_TYPE)
        }
        .map { it[DevicesTable.id].toString() }

/** Пересечение запрошенных target'ов с устройствами пользователя за один запрос (PERF-06). */
internal fun JdbcTransaction.filterOwnedDeviceIds(userId: String, deviceIds: List<String>): Set<String> {
    if (deviceIds.isEmpty()) return emptySet()
    return DevicesTable.selectAll()
        .where {
            (DevicesTable.userId eq Uuid.parse(userId)) and
                (DevicesTable.id inList deviceIds.map(Uuid::parse))
        }
        .map { it[DevicesTable.id].toString() }
        .toSet()
}

internal fun JdbcTransaction.countPermanentDevices(userId: String): Int =
    DevicesTable.selectAll()
        .where {
            (DevicesTable.userId eq Uuid.parse(userId)) and
                (DevicesTable.deviceType eq "permanent")
        }
        .count()
        .toInt()

/** Возвращает обновлённую запись или null, если устройство не принадлежит пользователю. */
internal fun JdbcTransaction.updateDeviceProfile(
    userId: String,
    deviceId: String,
    name: String?,
    icon: String?,
    osType: String? = null,
    now: Instant = Instant.now(),
): DeviceRecord? {
    val deviceUuid = Uuid.parse(deviceId)
    val updated = DevicesTable.update({
        (DevicesTable.id eq deviceUuid) and (DevicesTable.userId eq Uuid.parse(userId))
    }) {
        if (name != null) it[DevicesTable.name] = name
        if (icon != null) it[DevicesTable.icon] = icon
        if (osType != null) it[DevicesTable.osType] = osType
        it[lastSeenAt] = now.toOffsetDateTime()
    }
    if (updated == 0) return null
    return findDeviceById(userId, deviceId)
}

internal fun JdbcTransaction.touchDeviceLastSeen(userId: String, deviceId: String, at: Instant = Instant.now()) {
    DevicesTable.update({
        (DevicesTable.userId eq Uuid.parse(userId)) and (DevicesTable.id eq Uuid.parse(deviceId))
    }) {
        it[lastSeenAt] = at.toOffsetDateTime()
    }
}

internal fun JdbcTransaction.deleteDevice(userId: String, deviceId: String): Boolean =
    DevicesTable.deleteWhere {
        (DevicesTable.id eq Uuid.parse(deviceId)) and (DevicesTable.userId eq Uuid.parse(userId))
    } > 0

/**
 * Просроченные гости: возвращает id для SESSION_REVOKED перед удалением (§7.7).
 * Пачки ограничены, чтобы не держать лок и не раздувать `IN`-список (PERF-05).
 */
internal fun JdbcTransaction.deleteAndListExpiredGuests(now: Instant = Instant.now(), limit: Int = 1_000): List<ExpiredGuest> {
    val expired = DevicesTable.selectAll()
        .where {
            (DevicesTable.deviceType eq "guest") and
                (DevicesTable.expiresAt lessEq now.toOffsetDateTime())
        }
        .limit(limit)
        .map { ExpiredGuest(it[DevicesTable.userId].toString(), it[DevicesTable.id].toString()) }
    if (expired.isEmpty()) return emptyList()
    val condition = expired
        .map { (Uuid.parse(it.userId) to Uuid.parse(it.deviceId)) }
        .map { (userId, deviceId) ->
            (DevicesTable.userId eq userId) and (DevicesTable.id eq deviceId)
        }
        .reduce { acc, op -> acc or op }
    DevicesTable.deleteWhere { condition }
    return expired
}

/** Инкремент token_version одной командой инвалидирует все ранее выданные JWT (§7.8). */
internal fun JdbcTransaction.incrementUserTokenVersions(userId: String) {
    exec(
        "UPDATE devices SET token_version = token_version + 1 WHERE user_id = CAST(? AS uuid)",
        listOf(TextColumnType() to userId),
        StatementType.UPDATE,
    )
}

/** Атомарный инкремент без read-modify-write (FUNC-06). */
internal fun JdbcTransaction.incrementDeviceTokenVersion(userId: String, deviceId: String) {
    exec(
        "UPDATE devices SET token_version = token_version + 1 WHERE user_id = CAST(? AS uuid) AND id = CAST(? AS uuid)",
        listOf(TextColumnType() to userId, TextColumnType() to deviceId),
        StatementType.UPDATE,
    )
}

/** Лимит устройств: сериализует подсчёт под advisory lock (§7.6). */
internal fun JdbcTransaction.lockUserDevices(userId: String) = advisoryLock(NAMESPACE_USERS, userId)

/** Очередь: сериализует пару (insert, evict) на target (§9.2). */
internal fun JdbcTransaction.lockQueueTarget(deviceId: String) = advisoryLock(NAMESPACE_QUEUE, deviceId)

internal fun JdbcTransaction.lockRefreshFamily(familyId: String) = advisoryLock(NAMESPACE_REFRESH, familyId)

/** Сериализует first-insert состояния блокировки мастер-пароля (FUNC-06). */
internal fun JdbcTransaction.lockMasterPasswordState(userId: String) = advisoryLock(NAMESPACE_MASTER_PASSWORD, userId)

private fun JdbcTransaction.advisoryLock(namespace: Int, key: String) {
    exec(
        "SELECT pg_advisory_xact_lock(?, hashtext(?))",
        listOf(IntegerColumnType() to namespace, TextColumnType() to key),
        StatementType.SELECT,
    ) { rs -> rs.next() }
}

private fun toRecord(row: ResultRow): DeviceRecord = DeviceRecord(
    id = row[DevicesTable.id].toString(),
    userId = row[DevicesTable.userId].toString(),
    name = row[DevicesTable.name],
    osType = row[DevicesTable.osType],
    icon = row[DevicesTable.icon],
    deviceType = row[DevicesTable.deviceType],
    tokenVersion = row[DevicesTable.tokenVersion],
    createdAt = row[DevicesTable.createdAt].toInstantUtc(),
    lastSeenAt = row[DevicesTable.lastSeenAt].toInstantUtc(),
    expiresAt = row[DevicesTable.expiresAt]?.toInstantUtc(),
)

private const val NAMESPACE_USERS = 1
private const val NAMESPACE_QUEUE = 2
private const val NAMESPACE_REFRESH = 3
private const val NAMESPACE_MASTER_PASSWORD = 4
private const val PERMANENT_DEVICE_TYPE = "permanent"
