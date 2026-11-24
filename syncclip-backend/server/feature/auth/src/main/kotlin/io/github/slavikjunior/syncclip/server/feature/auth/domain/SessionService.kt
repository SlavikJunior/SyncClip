package io.github.slavikjunior.syncclip.server.feature.auth.domain

import io.github.slavikjunior.syncclip.server.contracts.error.ErrorCode
import io.github.slavikjunior.syncclip.server.contracts.error.ForbiddenException
import io.github.slavikjunior.syncclip.server.contracts.model.AuthContext
import io.github.slavikjunior.syncclip.server.contracts.port.NoopUserSessionGateway
import io.github.slavikjunior.syncclip.server.contracts.port.UserSessionGateway
import io.github.slavikjunior.syncclip.server.contracts.util.UuidV7
import io.github.slavikjunior.syncclip.server.contracts.ws.RevocationReason
import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.github.slavikjunior.syncclip.server.core.database.model.AttemptKind
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.findActiveRefreshChild
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.findDeviceById
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.findRefreshTokenByJtiHash
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.findRefreshTokenByJtiHashForUpdate
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.incrementDeviceTokenVersion
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.incrementUserTokenVersions
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.insertAuthAttempt
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.lockRefreshFamily
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.revokeActiveRefreshForDevice
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.revokeActiveRefreshForUser
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.revokeRefreshFamily
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.revokeRefreshToken
import io.github.slavikjunior.syncclip.server.core.database.tx
import io.github.slavikjunior.syncclip.server.core.security.JwtVerifier
import io.github.slavikjunior.syncclip.server.core.security.TokenException
import io.github.slavikjunior.syncclip.server.core.security.TokenHasher
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import java.time.Duration
import java.time.Instant

/**
 * Ротация и отзыв refresh-сессий (ARCH-01): отдельный use case вместо
 * «всё в AuthService». Логика та же: family-lock + FOR UPDATE (SEC-04),
 * reuse-detection, grace-окно, superseded → просто Invalid (FUNC-07).
 */
class SessionService(
    private val databaseFactory: DatabaseFactory,
    private val tokenStore: SessionTokenStore,
    private val verifier: JwtVerifier,
    private val sessionGateway: UserSessionGateway = NoopUserSessionGateway,
    private val clock: () -> Instant = Instant::now,
    private val metrics: io.github.slavikjunior.syncclip.server.contracts.metrics.MetricsSink =
        io.github.slavikjunior.syncclip.server.contracts.metrics.NoopMetricsSink,
) {

    suspend fun refresh(refreshToken: String): RefreshOutcome {
        val claims = try {
            verifier.verifyRefresh(refreshToken)
        } catch (e: TokenException.Expired) {
            return RefreshOutcome.Expired
        } catch (e: TokenException) {
            return RefreshOutcome.Invalid
        }
        val jtiHash = TokenHasher.sha256Hex(claims.jti)
        val outcome = tx(databaseFactory.database) {
            // Сначала читаем family_id, затем блокируем семейство и перечитываем
            // строку под FOR UPDATE: два параллельных refresh не могут оба увидеть
            // revoked_at = NULL и выпустить по ребёнку (SEC-04).
            val located = findRefreshTokenByJtiHash(jtiHash) ?: return@tx RefreshOutcome.Invalid
            lockRefreshFamily(located.familyId)
            val row = findRefreshTokenByJtiHashForUpdate(jtiHash) ?: return@tx RefreshOutcome.Invalid
            val device = findDeviceById(row.userId, row.deviceId) ?: return@tx RefreshOutcome.DeviceRevoked
            if (device.userId != row.userId || row.userId != claims.subject) {
                return@tx RefreshOutcome.Invalid
            }
            if (device.tokenVersion != claims.tokenVersion) {
                return@tx RefreshOutcome.DeviceRevoked
            }
            val now = clock()
            when {
                row.revokedAt != null -> handleRevoked(row, device, now)

                !row.expiresAt.isAfter(now) -> RefreshOutcome.Expired

                else -> {
                    revokeRefreshToken(row.id, RefreshReasons.ROTATED, now)
                    val tokens = tokenStore.issue(
                        tx = this,
                        userId = device.userId,
                        deviceId = device.id,
                        tokenVersion = device.tokenVersion,
                        now = now,
                        familyId = row.familyId,
                        parentId = row.id,
                    )
                    RefreshOutcome.Success(tokens)
                }
            }
        }
        if (outcome is RefreshOutcome.Reused) {
            metrics.increment(
                "syncclip_auth_failures_total",
                mapOf("code" to ErrorCode.REFRESH_TOKEN_REUSED.restCode),
            )
            sessionGateway.revokeDevice(outcome.userId, outcome.deviceId, RevocationReason.TOKEN_INVALIDATED)
        }
        return outcome
    }

    suspend fun logout(context: AuthContext, refreshToken: String?, allDevices: Boolean) {
        // Гость не управляет сессиями аккаунта: `allDevices` — только permanent (SEC-01).
        if (allDevices && context.isGuest) throw ForbiddenException(ErrorCode.FORBIDDEN_FOR_GUEST)
        val now = clock()
        tx(databaseFactory.database) {
            if (allDevices) {
                revokeActiveRefreshForUser(context.userId, RefreshReasons.LOGOUT, now)
                incrementUserTokenVersions(context.userId)
            } else {
                revokeProvidedFamily(refreshToken, context, now)
                revokeActiveRefreshForDevice(context.userId, context.deviceId, RefreshReasons.LOGOUT, now)
                incrementDeviceTokenVersion(context.userId, context.deviceId)
            }
        }
        if (allDevices) {
            sessionGateway.revokeAllOfUser(context.userId, RevocationReason.TOKEN_INVALIDATED)
        } else {
            sessionGateway.revokeDevice(context.userId, context.deviceId, RevocationReason.TOKEN_INVALIDATED)
        }
    }

    private fun JdbcTransaction.handleRevoked(
        row: io.github.slavikjunior.syncclip.server.core.database.model.RefreshTokenRecord,
        device: io.github.slavikjunior.syncclip.server.core.database.model.DeviceRecord,
        now: Instant,
    ): RefreshOutcome {
        // Повтор после logout/нового логина — просто недействителен: наказывать
        // семью нельзя, иначе старая сессия убивает новую (FUNC-07).
        if (row.revokeReason != RefreshReasons.ROTATED) return RefreshOutcome.Invalid
        val grace = Duration.between(row.revokedAt, now) <= REFRESH_GRACE_WINDOW
        val child = if (grace) findActiveRefreshChild(row.id) else null
        if (grace && child != null) {
            // Benign retry после сетевого сбоя: гасим ребёнка, выдаём новую пару.
            revokeRefreshToken(child.id, RefreshReasons.ROTATED, now)
            val tokens = tokenStore.issue(
                tx = this,
                userId = device.userId,
                deviceId = device.id,
                tokenVersion = device.tokenVersion,
                now = now,
                familyId = row.familyId,
                parentId = child.id,
            )
            return RefreshOutcome.Success(tokens)
        }
        // Reuse вне окна: компрометация → вся цепочка и token_version++.
        revokeRefreshFamily(row.familyId, RefreshReasons.REUSE, now)
        incrementDeviceTokenVersion(device.userId, device.id)
        insertAuthAttempt(
            id = UuidV7.newString(),
            userId = device.userId,
            deviceId = device.id,
            emailHash = null,
            ipAddress = null,
            userAgent = null,
            deviceModel = device.name,
            kind = AttemptKind.REFRESH_REUSE,
            success = false,
            at = now,
        )
        return RefreshOutcome.Reused(device.userId, device.id)
    }

    private fun JdbcTransaction.revokeProvidedFamily(
        refreshToken: String?,
        context: AuthContext,
        now: Instant,
    ) {
        if (refreshToken == null) return
        val jtiHash = runCatching { verifier.verifyRefresh(refreshToken) }
            .getOrNull()
            ?.let { claims -> TokenHasher.sha256Hex(claims.jti) }
            ?: return
        val row = findRefreshTokenByJtiHash(jtiHash) ?: return
        // deviceId теперь не глобальный (SEC-09): сверяем и владельца.
        if (row.userId != context.userId || row.deviceId != context.deviceId) return
        revokeRefreshFamily(row.familyId, RefreshReasons.LOGOUT, now)
    }
}
