package io.github.slavikjunior.syncclip.server.feature.auth.domain

import io.github.slavikjunior.syncclip.server.contracts.model.OsType
import io.github.slavikjunior.syncclip.server.core.database.model.DeviceRecord
import io.github.slavikjunior.syncclip.server.core.security.TokenPair

/** Метаданные запроса для журнала auth_attempts (PII — только здесь, §12.5). */
data class RequestMeta(
    val ip: String?,
    val userAgent: String?,
    val deviceModel: String?,
)

data class RegisterCommand(
    val email: String,
    val password: String,
    val deviceId: String,
    val deviceName: String,
    val osType: OsType,
)

data class LoginCommand(
    val email: String,
    val password: String,
    val deviceId: String,
    val deviceName: String,
    val osType: OsType,
)

/** Additive Firebase Auth: identity уже проверена, пароль не участвует. */
data class FirebaseLoginCommand(
    val deviceId: String,
    val deviceName: String,
    val osType: OsType,
)

data class AuthSession(
    val userId: String,
    val tokens: TokenPair,
    val device: DeviceRecord,
)

data class GuestExchangeCommand(
    val inviteToken: String,
    val deviceId: String,
    val deviceName: String,
    val osType: OsType,
)

data class GuestSession(
    val userId: String,
    val deviceId: String,
    val accessToken: String,
    val expiresAt: java.time.Instant,
)

data class GuestInviteView(
    val inviteToken: String,
    val expiresAt: java.time.Instant,
)

/** Результат refresh: ошибки возвращаются значением, чтобы коммит состоялся (§7.4). */
sealed interface RefreshOutcome {
    data class Success(val tokens: TokenPair) : RefreshOutcome
    data object Invalid : RefreshOutcome
    data object Expired : RefreshOutcome
    data class Reused(val userId: String, val deviceId: String) : RefreshOutcome
    data object DeviceRevoked : RefreshOutcome
}
