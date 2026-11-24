package io.github.slavikjunior.syncclip.server.feature.auth.domain

import io.github.slavikjunior.syncclip.server.contracts.error.ErrorCode
import io.github.slavikjunior.syncclip.server.contracts.error.ForbiddenException
import io.github.slavikjunior.syncclip.server.contracts.error.NotFoundException
import io.github.slavikjunior.syncclip.server.contracts.model.AuthContext
import io.github.slavikjunior.syncclip.server.contracts.port.NoopUserSessionGateway
import io.github.slavikjunior.syncclip.server.contracts.port.UserSessionGateway
import io.github.slavikjunior.syncclip.server.contracts.util.UuidV7
import io.github.slavikjunior.syncclip.server.core.config.AppConfig
import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.github.slavikjunior.syncclip.server.core.database.model.AttemptKind
import io.github.slavikjunior.syncclip.server.core.database.model.LockRecord
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.findCanary
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.findDeviceById
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.findLockStateForUpdate
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.insertAuthAttempt
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.insertCanaryOnce
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.listDevicesByUser
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.lockMasterPasswordState
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.saveLockState
import io.github.slavikjunior.syncclip.server.core.database.tx
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import io.github.slavikjunior.syncclip.server.core.push.PushMessage
import io.github.slavikjunior.syncclip.server.core.push.PushNotifier
import io.github.slavikjunior.syncclip.server.core.security.FailureOutcome
import io.github.slavikjunior.syncclip.server.core.security.LockState
import io.github.slavikjunior.syncclip.server.core.security.LockVerdict
import io.github.slavikjunior.syncclip.server.core.security.LockoutPolicy
import java.time.Instant

/**
 * Canary + серверный lockout мастер-пароля (FR-1.3/1.4, CG-01).
 * Сервер не расшифровывает canary и не проверяет мастер-пароль (§12.2).
 */
class MasterPasswordService(
    private val databaseFactory: DatabaseFactory,
    private val config: AppConfig,
    private val policy: LockoutPolicy,
    private val pushNotifier: PushNotifier,
    private val sessionGateway: UserSessionGateway = NoopUserSessionGateway,
    private val clock: () -> Instant = Instant::now,
) {

    suspend fun getCanary(context: AuthContext): String {
        requirePermanent(context)
        return tx(databaseFactory.database) {
            findCanary(context.userId) ?: throw NotFoundException(ErrorCode.CANARY_NOT_FOUND)
        }
    }

    suspend fun setCanary(context: AuthContext, encryptedCanary: String) {
        requirePermanent(context)
        val stored = tx(databaseFactory.database) {
            insertCanaryOnce(context.userId, encryptedCanary, clock())
        }
        if (!stored) throw io.github.slavikjunior.syncclip.server.contracts.error.ConflictException(
            ErrorCode.CANARY_ALREADY_SET,
        )
    }

    /** `success=false` → FCM на остальные доверенные устройства (FR-1.5). */
    suspend fun reportAttempt(context: AuthContext, success: Boolean, meta: RequestMeta): Int {
        requirePermanent(context)
        val now = clock()
        val result = tx(databaseFactory.database) {
            lockMasterPasswordState(context.userId)
            val current = findLockStateForUpdate(context.userId)
            val state = current?.toLockState() ?: LockState()
            if (success) {
                saveLockState(LockRecord(context.userId, 0, 0, null), now)
                insertAttempt(context, true, meta, now)
                AttemptResult.Reset
            } else {
                when (val outcome = policy.onFailure(state, now)) {
                    is FailureOutcome.Counted -> {
                        saveLockState(outcome.state.toRecord(context.userId), now)
                        insertAttempt(context, false, meta, now)
                        AttemptResult.Counted(outcome.remainingInGroup)
                    }

                    is FailureOutcome.Locked -> {
                        saveLockState(outcome.state.toRecord(context.userId), now)
                        insertAttempt(context, false, meta, now)
                        AttemptResult.Locked(outcome.retryAfter.seconds)
                    }
                }
            }
        }

        if (!success) {
            notifyOtherDevices(context, meta)
        }

        return when (result) {
            is AttemptResult.Reset -> policy.remainingAttempts(LockState(), now)
            is AttemptResult.Counted -> result.remaining
            is AttemptResult.Locked -> throw ForbiddenException(
                ErrorCode.MASTER_PASSWORD_LOCKED,
                retryAfterSeconds = result.retryAfterSeconds,
            )
        }
    }

    private fun JdbcTransaction.insertAttempt(
        context: AuthContext,
        success: Boolean,
        meta: RequestMeta,
        now: Instant,
    ) = insertAuthAttempt(
        id = UuidV7.newString(),
        userId = context.userId,
        deviceId = context.deviceId,
        emailHash = null,
        ipAddress = meta.ip,
        userAgent = meta.userAgent,
        // Модель из X-Device-Model; имя устройства — fallback (LOW-08).
        deviceModel = meta.deviceModel ?: context.deviceName,
        kind = ATTEMPT_KIND,
        success = success,
        at = now,
    )

    private fun notifyOtherDevices(context: AuthContext, meta: RequestMeta) {
        pushNotifier.notifyUser(
            userId = context.userId,
            message = PushMessage(
                title = "SyncClip: неверный мастер-пароль",
                body = buildString {
                    append("Устройство: ").append(context.deviceName)
                    if (meta.ip != null) append(", IP: ").append(meta.ip)
                },
                data = mapOf("type" to "master_password_failed"),
                collapseKey = "master_password",
            ),
            exceptDeviceId = context.deviceId,
        )
    }

    private fun requirePermanent(context: AuthContext) {
        if (context.isGuest) throw ForbiddenException(ErrorCode.FORBIDDEN_FOR_GUEST)
    }

    private fun LockRecord.toLockState(): LockState = LockState(
        failedInGroup = failedInGroup,
        lockoutCount = lockoutCount,
        lockedUntil = lockedUntil,
    )

    private fun LockState.toRecord(userId: String): LockRecord = LockRecord(
        userId = userId,
        failedInGroup = failedInGroup,
        lockoutCount = lockoutCount,
        lockedUntil = lockedUntil,
    )

    private sealed interface AttemptResult {
        data object Reset : AttemptResult
        data class Counted(val remaining: Int) : AttemptResult
        data class Locked(val retryAfterSeconds: Long) : AttemptResult
    }

    private companion object {
        const val ATTEMPT_KIND = AttemptKind.MASTER_PASSWORD
    }
}
