package io.github.slavikjunior.syncclip.server.feature.auth.domain

import io.github.slavikjunior.syncclip.server.contracts.error.ConflictException
import io.github.slavikjunior.syncclip.server.contracts.error.ErrorCode
import io.github.slavikjunior.syncclip.server.contracts.error.ForbiddenException
import io.github.slavikjunior.syncclip.server.contracts.error.GuestInviteException
import io.github.slavikjunior.syncclip.server.contracts.model.AuthContext
import io.github.slavikjunior.syncclip.server.contracts.model.DeviceIcon
import io.github.slavikjunior.syncclip.server.contracts.util.UuidV7
import io.github.slavikjunior.syncclip.server.core.config.AppConfig
import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.github.slavikjunior.syncclip.server.core.database.model.AttemptKind
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.findDeviceById
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.findGuestInviteByTokenHash
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.insertAuthAttempt
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.insertDevice
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.insertGuestInvite
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.markGuestInviteUsed
import io.github.slavikjunior.syncclip.server.core.database.tx
import io.github.slavikjunior.syncclip.server.core.security.JwtIssuer
import io.github.slavikjunior.syncclip.server.core.security.SecureTokens
import io.github.slavikjunior.syncclip.server.core.security.TokenHasher
import java.time.Instant

/**
 * Гостевой режим (FR-1.6, CG-02): одноразовый invite-код (в БД только SHA-256),
 * сессия 1 час, без refresh, management запрещён.
 */
class GuestService(
    private val databaseFactory: DatabaseFactory,
    private val config: AppConfig,
    private val issuer: JwtIssuer,
    private val tokens: SecureTokens,
    private val clock: () -> Instant = Instant::now,
) {

    suspend fun createInvite(context: AuthContext): GuestInviteView {
        if (context.isGuest) throw ForbiddenException(ErrorCode.FORBIDDEN_FOR_GUEST)
        val now = clock()
        val code = tokens.newInviteCode()
        val expiresAt = now.plusSeconds(config.security.guest.inviteTtlSeconds)
        tx(databaseFactory.database) {
            insertGuestInvite(
                id = UuidV7.newString(),
                userId = context.userId,
                createdByDeviceId = context.deviceId,
                tokenHash = TokenHasher.sha256Hex(code),
                createdAt = now,
                expiresAt = expiresAt,
            )
        }
        return GuestInviteView(inviteToken = code, expiresAt = expiresAt)
    }

    suspend fun exchange(command: GuestExchangeCommand, meta: RequestMeta): GuestSession {
        val tokenHash = TokenHasher.sha256Hex(command.inviteToken)
        val now = clock()
        return tx(databaseFactory.database) {
            val invite = findGuestInviteByTokenHash(tokenHash)
                ?: throw GuestInviteException(ErrorCode.INVALID_GUEST_INVITE)
            when {
                invite.revokedAt != null || invite.userId.isEmpty() ->
                    throw GuestInviteException(ErrorCode.INVALID_GUEST_INVITE)

                invite.usedAt != null ->
                    throw GuestInviteException(ErrorCode.GUEST_INVITE_ALREADY_USED)

                !invite.expiresAt.isAfter(now) ->
                    throw GuestInviteException(ErrorCode.GUEST_INVITE_EXPIRED)
            }
            if (findDeviceById(invite.userId, command.deviceId) != null) {
                throw ConflictException(ErrorCode.DEVICE_ID_CONFLICT)
            }
            val sessionTtl = config.security.guest.sessionTtlSeconds
            val expiresAt = now.plusSeconds(sessionTtl)
            // Устройство создаётся до «сжигания» приглашения: FK used_by_device_id
            // ссылается на devices. При проигранной гонке markGuestInviteUsed
            // вернёт false, и вся транзакция откатится (FUNC-06).
            val inserted = insertDevice(
                id = command.deviceId,
                userId = invite.userId,
                name = command.deviceName,
                osType = command.osType.wire,
                icon = DeviceIcon.defaultFor(command.osType).wire,
                deviceType = "guest",
                expiresAt = expiresAt,
                now = now,
            )
            if (!inserted) throw ConflictException(ErrorCode.DEVICE_ID_CONFLICT)
            if (!markGuestInviteUsed(invite.id, command.deviceId, now)) {
                throw GuestInviteException(ErrorCode.GUEST_INVITE_ALREADY_USED)
            }
            val (accessToken, _) = issuer.issueAccess(
                userId = invite.userId,
                deviceId = command.deviceId,
                tokenVersion = 0,
                ttlSeconds = sessionTtl,
            )
            insertAuthAttempt(
                id = UuidV7.newString(),
                userId = invite.userId,
                deviceId = command.deviceId,
                emailHash = null,
                ipAddress = meta.ip,
                userAgent = meta.userAgent,
                deviceModel = meta.deviceModel,
                kind = AttemptKind.GUEST_EXCHANGE,
                success = true,
                at = now,
            )
            GuestSession(
                userId = invite.userId,
                deviceId = command.deviceId,
                accessToken = accessToken,
                expiresAt = expiresAt,
            )
        }
    }

    /** `shareUri` формирует клиент (Share Sheet/QR/AirDrop), сервер отдаёт готовую ссылку. */
    fun shareUri(inviteToken: String): String = "syncclip://guest?token=$inviteToken"
}
