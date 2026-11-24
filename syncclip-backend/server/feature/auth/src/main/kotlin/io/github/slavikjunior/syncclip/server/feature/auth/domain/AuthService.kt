package io.github.slavikjunior.syncclip.server.feature.auth.domain

import io.github.slavikjunior.syncclip.server.contracts.error.ConflictException
import io.github.slavikjunior.syncclip.server.contracts.error.ErrorCode
import io.github.slavikjunior.syncclip.server.contracts.error.ForbiddenException
import io.github.slavikjunior.syncclip.server.contracts.error.UnauthorizedException
import io.github.slavikjunior.syncclip.server.contracts.model.DeviceIcon
import io.github.slavikjunior.syncclip.server.contracts.util.UuidV7
import io.github.slavikjunior.syncclip.server.core.config.AppConfig
import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.github.slavikjunior.syncclip.server.core.database.model.AttemptKind
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.countFailedAttemptsSince
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.countPermanentDevices
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.findDeviceById
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.findUserByEmail
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.findUserByFirebaseUid
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.insertFirebaseUser
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.linkFirebaseUid
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.insertAuthAttempt
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.insertDevice
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.insertUser
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.lastFailedAttemptAt
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.lastSuccessfulAttemptAt
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.listDevicesByUser
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.lockUserDevices
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.revokeActiveRefreshForDevice
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.updateDeviceProfile
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.updateUserPasswordHash
import io.github.slavikjunior.syncclip.server.core.database.tx
import io.github.slavikjunior.syncclip.server.core.push.PushMessage
import io.github.slavikjunior.syncclip.server.core.push.PushNotifier
import io.github.slavikjunior.syncclip.server.core.security.AuditHasher
import io.github.slavikjunior.syncclip.server.core.security.PasswordHasher
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import java.time.Duration
import java.time.Instant

/**
 * Регистрация и вход (§7.3). Ротация/отзыв сессий — в [SessionService],
 * выпуск токенов — в [SessionTokenStore] (ARCH-01).
 *
 * Дисциплина транзакций: одна tx на use case; хеширование пароля — вне tx;
 * push — только после commit (§4.4).
 */
class AuthService(
    private val databaseFactory: DatabaseFactory,
    private val hasher: PasswordHasher,
    private val auditHasher: AuditHasher,
    private val tokenStore: SessionTokenStore,
    private val config: AppConfig,
    private val pushNotifier: PushNotifier,
    private val clock: () -> Instant = Instant::now,
    private val metrics: io.github.slavikjunior.syncclip.server.contracts.metrics.MetricsSink =
        io.github.slavikjunior.syncclip.server.contracts.metrics.NoopMetricsSink,
) {

    /** Хеш-пустышка для выравнивания времени ответа при неизвестном email (§7.2). */
    private val dummyHash: String by lazy { hasher.hash("syncclip-dummy-password") }

    suspend fun register(command: RegisterCommand, meta: RequestMeta): AuthSession {
        val now = clock()
        val passwordHash = hasher.hash(command.password)
        return tx(databaseFactory.database) {
            if (findUserByEmail(command.email) != null) {
                throw ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED)
            }
            val userId = UuidV7.newString()
            if (!insertUser(userId, command.email, passwordHash, hasher.algorithm, now = now)) {
                throw ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED)
            }
            lockUserDevices(userId)
            val deviceInserted = insertDevice(
                id = command.deviceId,
                userId = userId,
                name = command.deviceName,
                osType = command.osType.wire,
                icon = DeviceIcon.defaultFor(command.osType).wire,
                deviceType = PERMANENT,
                expiresAt = null,
                now = now,
            )
            if (!deviceInserted) throw ConflictException(ErrorCode.DEVICE_ID_CONFLICT)
            val tokens = tokenStore.issue(
                tx = this,
                userId = userId,
                deviceId = command.deviceId,
                tokenVersion = 0,
                now = now,
            )
            insertAuthAttempt(
                id = UuidV7.newString(),
                userId = userId,
                deviceId = command.deviceId,
                emailHash = null,
                ipAddress = meta.ip,
                userAgent = meta.userAgent,
                deviceModel = meta.deviceModel,
                kind = AttemptKind.LOGIN,
                success = true,
                at = now,
            )
            val device = checkNotNull(findDeviceById(userId, command.deviceId)) { "device vanished after insert" }
            AuthSession(userId, tokens, device)
        }
    }

    suspend fun login(command: LoginCommand, meta: RequestMeta): AuthSession {
        val user = tx(databaseFactory.database) { findUserByEmail(command.email) }
        if (user == null) {
            // Dummy-verify выравнивает время ответа и не раскрывает существование аккаунта.
            hasher.verify(command.password, dummyHash)
            metrics.increment("syncclip_auth_failures_total", mapOf("code" to ErrorCode.INVALID_CREDENTIALS.restCode))
            tx(databaseFactory.database) {
                insertAuthAttempt(
                    id = UuidV7.newString(),
                    userId = null,
                    deviceId = null,
                    emailHash = auditHasher.emailHash(command.email),
                    ipAddress = meta.ip,
                    userAgent = meta.userAgent,
                    deviceModel = meta.deviceModel,
                    kind = AttemptKind.LOGIN,
                    success = false,
                )
            }
            throw UnauthorizedException(ErrorCode.INVALID_CREDENTIALS)
        }

        // Блокировка проверяется до пароля: иначе правильный пароль обходит lockout (SEC-06).
        val now = clock()
        loginLockRetryAfter(user.id, now)?.let {
            // Lockout маскируется под invalid_credentials: не раскрываем существование
            // аккаунта и состояние блокировки (SEC-10).
            metrics.increment("syncclip_auth_failures_total", mapOf("code" to ErrorCode.INVALID_CREDENTIALS.restCode))
            throw UnauthorizedException(ErrorCode.INVALID_CREDENTIALS)
        }

        if (!hasher.verify(command.password, user.passwordHash)) {
            val lockout = config.security.loginLockout
            val failures = tx(databaseFactory.database) {
                insertAuthAttempt(
                    id = UuidV7.newString(),
                    userId = user.id,
                    deviceId = null,
                    emailHash = null,
                    ipAddress = meta.ip,
                    userAgent = meta.userAgent,
                    deviceModel = meta.deviceModel,
                    kind = AttemptKind.LOGIN,
                    success = false,
                    at = now,
                )
                if (lockout.enabled) {
                    countRecentFailures(user.id, now)
                } else {
                    0
                }
            }
            if (lockout.enabled && failures >= lockout.failures) {
                metrics.increment(
                    "syncclip_auth_failures_total",
                    mapOf("code" to ErrorCode.INVALID_CREDENTIALS.restCode),
                )
                throw UnauthorizedException(ErrorCode.INVALID_CREDENTIALS)
            }
            metrics.increment("syncclip_auth_failures_total", mapOf("code" to ErrorCode.INVALID_CREDENTIALS.restCode))
            throw UnauthorizedException(ErrorCode.INVALID_CREDENTIALS)
        }

        // Хеширование вне транзакции: 600k итераций не должны держать соединение.
        val upgradedHash = if (hasher.needsRehash(user.passwordHash)) {
            hasher.hash(command.password)
        } else {
            null
        }

        val outcome = tx(databaseFactory.database) {
            val existing = findDeviceById(user.id, command.deviceId)
            var newDevice = false
            if (existing != null) {
                updateDeviceProfile(
                    userId = user.id,
                    deviceId = command.deviceId,
                    name = command.deviceName,
                    icon = DeviceIcon.defaultFor(command.osType).wire,
                    // Переустановка/смена ОС должны обновлять профиль (FUNC-08).
                    osType = command.osType.wire,
                    now = now,
                )
            } else {
                lockUserDevices(user.id)
                if (countPermanentDevices(user.id) >= config.security.deviceLimitFree) {
                    throw ForbiddenException(
                        ErrorCode.DEVICE_LIMIT_EXCEEDED,
                        details = removableDevices(user.id),
                    )
                }
                val deviceInserted = insertDevice(
                    id = command.deviceId,
                    userId = user.id,
                    name = command.deviceName,
                    osType = command.osType.wire,
                    icon = DeviceIcon.defaultFor(command.osType).wire,
                    deviceType = PERMANENT,
                    expiresAt = null,
                    now = now,
                )
                if (!deviceInserted) throw ConflictException(ErrorCode.DEVICE_ID_CONFLICT)
                newDevice = true
            }
            val device = checkNotNull(findDeviceById(user.id, command.deviceId)) { "device vanished after upsert" }
            if (upgradedHash != null) updateUserPasswordHash(user.id, upgradedHash, hasher.algorithm)
            // Одна активная сессия на устройство: прежние семейства гасятся.
            revokeActiveRefreshForDevice(user.id, device.id, RefreshReasons.SUPERSEDED, now)
            val tokens = tokenStore.issue(
                tx = this,
                userId = user.id,
                deviceId = device.id,
                tokenVersion = device.tokenVersion,
                now = now,
            )
            insertAuthAttempt(
                id = UuidV7.newString(),
                userId = user.id,
                deviceId = device.id,
                emailHash = null,
                ipAddress = meta.ip,
                userAgent = meta.userAgent,
                deviceModel = meta.deviceModel,
                kind = AttemptKind.LOGIN,
                success = true,
                at = now,
            )
            Triple(AuthSession(user.id, tokens, device), newDevice, device.name)
        }

        if (outcome.second) {
            pushNotifier.notifyUser(
                userId = user.id,
                message = PushMessage(
                    title = "SyncClip",
                    body = "Новое устройство: ${outcome.third}",
                    data = mapOf("type" to "new_device", "deviceId" to command.deviceId),
                    collapseKey = "new_device",
                ),
                exceptDeviceId = command.deviceId,
            )
        }
        return outcome.first
    }

    /**
     * Additive Firebase login: identity проверена [FirebaseVerifier], сервер
     * сопоставляет `sub`/email с аккаунтом и выпускает собственные JWT.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod", "ThrowsCount")
    suspend fun firebaseLogin(
        identity: FirebaseIdentity,
        command: FirebaseLoginCommand,
        meta: RequestMeta,
    ): AuthSession {
        if (config.auth.firebaseRequireVerifiedEmail && !identity.emailVerified) {
            throw ForbiddenException(ErrorCode.EMAIL_NOT_VERIFIED)
        }
        val now = clock()
        val outcome = tx(databaseFactory.database) {
            var user = findUserByFirebaseUid(identity.uid)
            if (user == null) {
                val byEmail = findUserByEmail(identity.email)
                user = when {
                    byEmail == null -> {
                        val newUserId = UuidV7.newString()
                        if (insertFirebaseUser(newUserId, identity.email, identity.uid, now)) {
                            findUserByFirebaseUid(identity.uid)
                        } else {
                            // Гонка: параллельный запрос уже создал/привязал аккаунт.
                            findUserByFirebaseUid(identity.uid) ?: findUserByEmail(identity.email)
                        }
                    }

                    byEmail.firebaseUid != null && byEmail.firebaseUid != identity.uid ->
                        throw ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED)

                    byEmail.firebaseUid == null -> {
                        if (linkFirebaseUid(byEmail.id, identity.uid)) {
                            findUserByEmail(identity.email)
                        } else {
                            findUserByFirebaseUid(identity.uid)
                        }
                    }

                    else -> byEmail
                }
            }
            val account = user ?: throw UnauthorizedException(ErrorCode.FIREBASE_TOKEN_INVALID)
            val existing = findDeviceById(account.id, command.deviceId)
            var newDevice = false
            if (existing != null) {
                updateDeviceProfile(
                    userId = account.id,
                    deviceId = command.deviceId,
                    name = command.deviceName,
                    icon = DeviceIcon.defaultFor(command.osType).wire,
                    osType = command.osType.wire,
                    now = now,
                )
            } else {
                lockUserDevices(account.id)
                if (countPermanentDevices(account.id) >= config.security.deviceLimitFree) {
                    throw ForbiddenException(
                        ErrorCode.DEVICE_LIMIT_EXCEEDED,
                        details = removableDevices(account.id),
                    )
                }
                val inserted = insertDevice(
                    id = command.deviceId,
                    userId = account.id,
                    name = command.deviceName,
                    osType = command.osType.wire,
                    icon = DeviceIcon.defaultFor(command.osType).wire,
                    deviceType = PERMANENT,
                    expiresAt = null,
                    now = now,
                )
                if (!inserted) throw ConflictException(ErrorCode.DEVICE_ID_CONFLICT)
                newDevice = true
            }
            val device = checkNotNull(findDeviceById(account.id, command.deviceId)) {
                "device vanished after firebase upsert"
            }
            revokeActiveRefreshForDevice(account.id, device.id, RefreshReasons.SUPERSEDED, now)
            val tokens = tokenStore.issue(
                tx = this,
                userId = account.id,
                deviceId = device.id,
                tokenVersion = device.tokenVersion,
                now = now,
            )
            insertAuthAttempt(
                id = UuidV7.newString(),
                userId = account.id,
                deviceId = device.id,
                emailHash = null,
                ipAddress = meta.ip,
                userAgent = meta.userAgent,
                deviceModel = meta.deviceModel,
                kind = AttemptKind.LOGIN,
                success = true,
                at = now,
            )
            Triple(AuthSession(account.id, tokens, device), newDevice, device.name)
        }

        if (outcome.second) {
            pushNotifier.notifyUser(
                userId = outcome.first.userId,
                message = PushMessage(
                    title = "SyncClip",
                    body = "Новое устройство: ${outcome.third}",
                    data = mapOf("type" to "new_device", "deviceId" to command.deviceId),
                    collapseKey = "new_device",
                ),
                exceptDeviceId = command.deviceId,
            )
        }
        return outcome.first
    }

    /** Сколько секунд осталось до конца active login-lock; `null` — не заблокирован. */
    private suspend fun loginLockRetryAfter(userId: String, now: Instant): Long? {
        val lockout = config.security.loginLockout
        if (!lockout.enabled) return null
        return tx(databaseFactory.database) {
            val windowStart = now.minus(Duration.ofMinutes(lockout.windowMinutes))
            val since = lastSuccessfulAttemptAt(userId, AttemptKind.LOGIN, windowStart) ?: windowStart
            val failures = countFailedAttemptsSince(userId, AttemptKind.LOGIN, since)
            if (failures < lockout.failures) return@tx null
            val lastFailure = lastFailedAttemptAt(userId, AttemptKind.LOGIN, since) ?: return@tx null
            val until = lastFailure.plus(Duration.ofMinutes(lockout.lockMinutes))
            if (until.isAfter(now)) Duration.between(now, until).seconds.coerceAtLeast(1) else null
        }
    }

    private fun JdbcTransaction.countRecentFailures(userId: String, now: Instant): Int {
        val lockout = config.security.loginLockout
        val windowStart = now.minus(Duration.ofMinutes(lockout.windowMinutes))
        val since = lastSuccessfulAttemptAt(userId, AttemptKind.LOGIN, windowStart) ?: windowStart
        return countFailedAttemptsSince(userId, AttemptKind.LOGIN, since)
    }

    private fun JdbcTransaction.removableDevices(userId: String): Map<String, String> {
        val ids = listDevicesByUser(userId)
            .filterNot { it.isGuest }
            .take(config.security.deviceLimitFree)
            .joinToString(",") { it.id }
        return if (ids.isEmpty()) emptyMap() else mapOf("removableDevices" to ids)
    }

    private companion object {
        const val PERMANENT = "permanent"
    }
}
