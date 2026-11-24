@file:Suppress("MaxLineLength", "TooManyFunctions")

package io.github.slavikjunior.syncclip.server.core.database.repository

import io.github.slavikjunior.syncclip.server.core.database.model.UserRecord
import io.github.slavikjunior.syncclip.server.core.database.model.DeviceRecord
import io.github.slavikjunior.syncclip.server.core.database.model.AttemptRecord
import io.github.slavikjunior.syncclip.server.core.database.model.RefreshTokenRecord
import io.github.slavikjunior.syncclip.server.core.database.model.LockRecord
import io.github.slavikjunior.syncclip.server.core.database.model.GuestInviteRecord
import io.github.slavikjunior.syncclip.server.core.database.model.QueuedPacketRecord
import io.github.slavikjunior.syncclip.server.core.database.model.FcmTokenRecord
import io.github.slavikjunior.syncclip.server.core.database.model.AckWatermark
import io.github.slavikjunior.syncclip.server.core.database.model.ExpiredGuest
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import java.time.Instant

import io.github.slavikjunior.syncclip.server.core.database.repository.findUserByEmail as findUserByEmailInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.findUserByFirebaseUid as findUserByFirebaseUidInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.insertFirebaseUser as insertFirebaseUserInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.linkFirebaseUid as linkFirebaseUidInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.findDeviceById as findDeviceByIdInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.insertAuthAttempt as insertAuthAttemptInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.insertDevice as insertDeviceInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.insertUser as insertUserInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.listDevicesByUser as listDevicesByUserInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.updateDeviceProfile as updateDeviceProfileInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.updateUserPasswordHash as updateUserPasswordHashInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.lockUserDevices as lockUserDevicesInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.countPermanentDevices as countPermanentDevicesInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.incrementUserTokenVersions as incrementUserTokenVersionsInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.incrementDeviceTokenVersion as incrementDeviceTokenVersionInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.deleteDevice as deleteDeviceInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.deleteAndListExpiredGuests as deleteAndListExpiredGuestsInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.touchDeviceLastSeen as touchDeviceLastSeenInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.filterOwnedDeviceIds as filterOwnedDeviceIdsInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.listPermanentDeviceIds as listPermanentDeviceIdsInternal

import io.github.slavikjunior.syncclip.server.core.database.repository.countFailedAttemptsSince as countFailedAttemptsSinceInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.listFailedAttempts as listFailedAttemptsInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.lastFailedAttemptAt as lastFailedAttemptAtInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.lastSuccessfulAttemptAt as lastSuccessfulAttemptAtInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.deleteAttemptsOlderThan as deleteAttemptsOlderThanInternal

import io.github.slavikjunior.syncclip.server.core.database.repository.findRefreshTokenByJtiHash as findRefreshTokenByJtiHashInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.findRefreshTokenByJtiHashForUpdate as findRefreshTokenByJtiHashForUpdateInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.findActiveRefreshChild as findActiveRefreshChildInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.insertRefreshToken as insertRefreshTokenInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.revokeRefreshToken as revokeRefreshTokenInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.revokeRefreshFamily as revokeRefreshFamilyInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.revokeActiveRefreshForDevice as revokeActiveRefreshForDeviceInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.revokeActiveRefreshForUser as revokeActiveRefreshForUserInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.lockRefreshFamily as lockRefreshFamilyInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.deleteExpiredRefreshTokens as deleteExpiredRefreshTokensInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.deleteRevokedRefreshTokensBefore as deleteRevokedRefreshTokensBeforeInternal

import io.github.slavikjunior.syncclip.server.core.database.repository.findCanary as findCanaryInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.insertCanaryOnce as insertCanaryOnceInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.findLockStateForUpdate as findLockStateForUpdateInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.saveLockState as saveLockStateInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.lockMasterPasswordState as lockMasterPasswordStateInternal

import io.github.slavikjunior.syncclip.server.core.database.repository.insertGuestInvite as insertGuestInviteInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.findGuestInviteByTokenHash as findGuestInviteByTokenHashInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.markGuestInviteUsed as markGuestInviteUsedInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.deleteStaleGuestInvites as deleteStaleGuestInvitesInternal

import io.github.slavikjunior.syncclip.server.core.database.repository.insertQueuedPacketIgnore as insertQueuedPacketIgnoreInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.countQueuedPackets as countQueuedPacketsInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.evictOldestQueuedPackets as evictOldestQueuedPacketsInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.findQueueAnchorSeq as findQueueAnchorSeqInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.findQueueAnchorUserId as findQueueAnchorUserIdInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.listPendingPackets as listPendingPacketsInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.countPendingPackets as countPendingPacketsInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.deleteQueuedPacketByAck as deleteQueuedPacketByAckInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.deleteQueuedPacketsByTarget as deleteQueuedPacketsByTargetInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.deleteExpiredQueuedPackets as deleteExpiredQueuedPacketsInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.countQueuedPacketsTotal as countQueuedPacketsTotalInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.lockQueueTarget as lockQueueTargetInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.findAckWatermark as findAckWatermarkInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.saveAckWatermark as saveAckWatermarkInternal

import io.github.slavikjunior.syncclip.server.core.database.repository.upsertFcmToken as upsertFcmTokenInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.listFcmTokensForUser as listFcmTokensForUserInternal
import io.github.slavikjunior.syncclip.server.core.database.repository.deleteFcmTokenByToken as deleteFcmTokenByTokenInternal

/**
 * ARCH-01: фасад репозиториев `core:database`.
 *
 * Публичные top-level функции репозиториев стали `internal`; фичи импортируют
 * extension-функции этого объекта и не видят внутреннюю реализацию напрямую.
 * Транзакционный контекст (`JdbcTransaction`) по-прежнему передаётся неявно
 * через import extension'а внутри `tx {}` — без изменения структуры сервисов.
 */
object Repositories {

    fun JdbcTransaction.findUserByEmail(email: String): UserRecord? = findUserByEmailInternal(email)

    fun JdbcTransaction.findUserByFirebaseUid(firebaseUid: String): UserRecord? =
        findUserByFirebaseUidInternal(firebaseUid)

    fun JdbcTransaction.insertFirebaseUser(
        id: String,
        email: String,
        firebaseUid: String,
        now: Instant,
    ): Boolean = insertFirebaseUserInternal(id, email, firebaseUid, now)

    fun JdbcTransaction.linkFirebaseUid(userId: String, firebaseUid: String): Boolean =
        linkFirebaseUidInternal(userId, firebaseUid)

    fun JdbcTransaction.findDeviceById(userId: String, id: String): DeviceRecord? = findDeviceByIdInternal(userId, id)

    fun JdbcTransaction.insertUser(
        id: String,
        email: String,
        passwordHash: String,
        passwordAlgo: String,
        plan: String = "free",
        now: Instant = Instant.now(),
    ): Boolean = insertUserInternal(id, email, passwordHash, passwordAlgo, plan, now)

    fun JdbcTransaction.insertDevice(
        id: String,
        userId: String,
        name: String,
        osType: String,
        icon: String,
        deviceType: String,
        expiresAt: Instant?,
        now: Instant = Instant.now(),
    ): Boolean = insertDeviceInternal(id, userId, name, osType, icon, deviceType, expiresAt, now)

    fun JdbcTransaction.updateDeviceProfile(
        userId: String,
        deviceId: String,
        name: String?,
        icon: String?,
        osType: String? = null,
        now: Instant = Instant.now(),
    ): DeviceRecord? = updateDeviceProfileInternal(userId, deviceId, name, icon, osType, now)

    fun JdbcTransaction.updateUserPasswordHash(id: String, passwordHash: String, passwordAlgo: String) =
        updateUserPasswordHashInternal(id, passwordHash, passwordAlgo)

    fun JdbcTransaction.lockUserDevices(userId: String) = lockUserDevicesInternal(userId)

    fun JdbcTransaction.countPermanentDevices(userId: String): Int = countPermanentDevicesInternal(userId)

    fun JdbcTransaction.listDevicesByUser(userId: String): List<DeviceRecord> = listDevicesByUserInternal(userId)

    fun JdbcTransaction.listPermanentDeviceIds(userId: String): List<String> = listPermanentDeviceIdsInternal(userId)

    fun JdbcTransaction.filterOwnedDeviceIds(userId: String, deviceIds: List<String>): Set<String> =
        filterOwnedDeviceIdsInternal(userId, deviceIds)

    fun JdbcTransaction.deleteDevice(userId: String, deviceId: String): Boolean = deleteDeviceInternal(userId, deviceId)

    fun JdbcTransaction.deleteAndListExpiredGuests(now: Instant = Instant.now(), limit: Int = 1_000): List<ExpiredGuest> =
        deleteAndListExpiredGuestsInternal(now, limit)

    fun JdbcTransaction.touchDeviceLastSeen(userId: String, deviceId: String, at: Instant = Instant.now()) =
        touchDeviceLastSeenInternal(userId, deviceId, at)

    fun JdbcTransaction.incrementUserTokenVersions(userId: String) = incrementUserTokenVersionsInternal(userId)

    fun JdbcTransaction.incrementDeviceTokenVersion(userId: String, deviceId: String) =
        incrementDeviceTokenVersionInternal(userId, deviceId)

    fun JdbcTransaction.insertAuthAttempt(
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
    ) = insertAuthAttemptInternal(id, userId, deviceId, emailHash, ipAddress, userAgent, deviceModel, kind, success, at)

    fun JdbcTransaction.countFailedAttemptsSince(userId: String, kind: String, since: Instant): Int =
        countFailedAttemptsSinceInternal(userId, kind, since)

    fun JdbcTransaction.listFailedAttempts(
        userId: String,
        kinds: List<String>,
        notBefore: Instant,
        before: Instant,
        beforeId: String?,
        limit: Int,
    ): List<AttemptRecord> = listFailedAttemptsInternal(userId, kinds, notBefore, before, beforeId, limit)

    fun JdbcTransaction.lastFailedAttemptAt(userId: String, kind: String, since: Instant): Instant? =
        lastFailedAttemptAtInternal(userId, kind, since)

    fun JdbcTransaction.lastSuccessfulAttemptAt(userId: String, kind: String, since: Instant): Instant? =
        lastSuccessfulAttemptAtInternal(userId, kind, since)

    fun JdbcTransaction.deleteAttemptsOlderThan(threshold: Instant, limit: Int = 5_000): Int =
        deleteAttemptsOlderThanInternal(threshold, limit)

    fun JdbcTransaction.findRefreshTokenByJtiHash(jtiHash: String): RefreshTokenRecord? =
        findRefreshTokenByJtiHashInternal(jtiHash)

    fun JdbcTransaction.findRefreshTokenByJtiHashForUpdate(jtiHash: String): RefreshTokenRecord? =
        findRefreshTokenByJtiHashForUpdateInternal(jtiHash)

    fun JdbcTransaction.findActiveRefreshChild(parentId: String): RefreshTokenRecord? =
        findActiveRefreshChildInternal(parentId)

    fun JdbcTransaction.insertRefreshToken(
        id: String,
        userId: String,
        deviceId: String,
        familyId: String,
        parentId: String?,
        jtiHash: String,
        issuedAt: Instant,
        expiresAt: Instant,
    ) = insertRefreshTokenInternal(id, userId, deviceId, familyId, parentId, jtiHash, issuedAt, expiresAt)

    fun JdbcTransaction.revokeRefreshToken(id: String, reason: String, at: Instant = Instant.now()) =
        revokeRefreshTokenInternal(id, reason, at)

    fun JdbcTransaction.revokeRefreshFamily(familyId: String, reason: String, at: Instant = Instant.now()): Int =
        revokeRefreshFamilyInternal(familyId, reason, at)

    fun JdbcTransaction.revokeActiveRefreshForDevice(
        userId: String,
        deviceId: String,
        reason: String,
        at: Instant = Instant.now(),
    ): Int = revokeActiveRefreshForDeviceInternal(userId, deviceId, reason, at)

    fun JdbcTransaction.revokeActiveRefreshForUser(userId: String, reason: String, at: Instant = Instant.now()): Int =
        revokeActiveRefreshForUserInternal(userId, reason, at)

    fun JdbcTransaction.lockRefreshFamily(familyId: String) = lockRefreshFamilyInternal(familyId)

    fun JdbcTransaction.deleteExpiredRefreshTokens(now: Instant = Instant.now(), limit: Int = 1_000): Int =
        deleteExpiredRefreshTokensInternal(now, limit)

    fun JdbcTransaction.deleteRevokedRefreshTokensBefore(threshold: Instant, limit: Int = 1_000): Int =
        deleteRevokedRefreshTokensBeforeInternal(threshold, limit)

    fun JdbcTransaction.findCanary(userId: String): String? = findCanaryInternal(userId)

    fun JdbcTransaction.insertCanaryOnce(userId: String, encryptedCanary: String, now: Instant = Instant.now()): Boolean =
        insertCanaryOnceInternal(userId, encryptedCanary, now)

    fun JdbcTransaction.findLockStateForUpdate(userId: String): LockRecord? = findLockStateForUpdateInternal(userId)

    fun JdbcTransaction.saveLockState(record: LockRecord, now: Instant = Instant.now()) =
        saveLockStateInternal(record, now)

    fun JdbcTransaction.lockMasterPasswordState(userId: String) = lockMasterPasswordStateInternal(userId)

    fun JdbcTransaction.insertGuestInvite(
        id: String,
        userId: String,
        createdByDeviceId: String,
        tokenHash: String,
        createdAt: Instant,
        expiresAt: Instant,
    ) = insertGuestInviteInternal(id, userId, createdByDeviceId, tokenHash, createdAt, expiresAt)

    fun JdbcTransaction.findGuestInviteByTokenHash(tokenHash: String): GuestInviteRecord? =
        findGuestInviteByTokenHashInternal(tokenHash)

    fun JdbcTransaction.markGuestInviteUsed(id: String, usedByDeviceId: String, at: Instant = Instant.now()): Boolean =
        markGuestInviteUsedInternal(id, usedByDeviceId, at)

    fun JdbcTransaction.deleteStaleGuestInvites(before: Instant, limit: Int = 1_000): Int =
        deleteStaleGuestInvitesInternal(before, limit)

    fun JdbcTransaction.insertQueuedPacketIgnore(
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
    ): Boolean = insertQueuedPacketIgnoreInternal(
        id, userId, targetDeviceId, eventId, originDeviceId, encryptedPayload, payloadHash,
        clientTimestamp, createdAt, expiresAt,
    )

    fun JdbcTransaction.countQueuedPackets(userId: String, targetDeviceId: String): Int =
        countQueuedPacketsInternal(userId, targetDeviceId)

    fun JdbcTransaction.evictOldestQueuedPackets(userId: String, targetDeviceId: String, excess: Int): Int =
        evictOldestQueuedPacketsInternal(userId, targetDeviceId, excess)

    fun JdbcTransaction.findQueueAnchorSeq(userId: String, targetDeviceId: String, eventId: String): Long? =
        findQueueAnchorSeqInternal(userId, targetDeviceId, eventId)

    fun JdbcTransaction.findQueueAnchorUserId(eventId: String): String? = findQueueAnchorUserIdInternal(eventId)

    fun JdbcTransaction.listPendingPackets(
        userId: String,
        targetDeviceId: String,
        afterSeq: Long?,
        limit: Int,
    ): List<QueuedPacketRecord> = listPendingPacketsInternal(userId, targetDeviceId, afterSeq, limit)

    fun JdbcTransaction.countPendingPackets(userId: String, targetDeviceId: String, afterSeq: Long?): Int =
        countPendingPacketsInternal(userId, targetDeviceId, afterSeq)

    fun JdbcTransaction.deleteQueuedPacketByAck(userId: String, targetDeviceId: String, eventId: String): Boolean =
        deleteQueuedPacketByAckInternal(userId, targetDeviceId, eventId)

    fun JdbcTransaction.deleteQueuedPacketsByTarget(userId: String, targetDeviceId: String): Int =
        deleteQueuedPacketsByTargetInternal(userId, targetDeviceId)

    fun JdbcTransaction.deleteExpiredQueuedPackets(now: Instant = Instant.now(), limit: Int = 5_000): Int =
        deleteExpiredQueuedPacketsInternal(now, limit)

    fun JdbcTransaction.countQueuedPacketsTotal(): Int = countQueuedPacketsTotalInternal()

    fun JdbcTransaction.lockQueueTarget(deviceId: String) = lockQueueTargetInternal(deviceId)

    fun JdbcTransaction.findAckWatermark(userId: String, deviceId: String): AckWatermark? =
        findAckWatermarkInternal(userId, deviceId)

    fun JdbcTransaction.saveAckWatermark(
        userId: String,
        deviceId: String,
        eventId: String,
        seq: Long,
        at: Instant = Instant.now(),
    ) = saveAckWatermarkInternal(userId, deviceId, eventId, seq, at)

    fun JdbcTransaction.upsertFcmToken(
        id: String,
        userId: String,
        deviceId: String,
        token: String,
        now: Instant = Instant.now(),
    ) = upsertFcmTokenInternal(id, userId, deviceId, token, now)

    fun JdbcTransaction.listFcmTokensForUser(userId: String, exceptDeviceId: String? = null): List<String> =
        listFcmTokensForUserInternal(userId, exceptDeviceId)

    fun JdbcTransaction.deleteFcmTokenByToken(token: String): Int = deleteFcmTokenByTokenInternal(token)
}
