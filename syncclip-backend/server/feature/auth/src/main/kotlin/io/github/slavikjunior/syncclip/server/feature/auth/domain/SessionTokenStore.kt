package io.github.slavikjunior.syncclip.server.feature.auth.domain

import io.github.slavikjunior.syncclip.server.contracts.util.UuidV7
import io.github.slavikjunior.syncclip.server.core.config.AppConfig
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.insertRefreshToken
import io.github.slavikjunior.syncclip.server.core.security.JwtIssuer
import io.github.slavikjunior.syncclip.server.core.security.TokenHasher
import io.github.slavikjunior.syncclip.server.core.security.TokenPair
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import java.time.Instant

/**
 * Выпуск пары токенов + запись refresh-строки (ARCH-01).
 * Вынесено из AuthService, чтобы регистрация/вход и refresh/logout не дублировали
 * одну и ту же транзакционную логику.
 */
class SessionTokenStore(
    private val issuer: JwtIssuer,
    private val config: AppConfig,
) {

    fun issue(
        tx: JdbcTransaction,
        userId: String,
        deviceId: String,
        tokenVersion: Int,
        now: Instant,
        familyId: String? = null,
        parentId: String? = null,
    ): TokenPair {
        val tokens = issuer.issuePair(userId, deviceId, tokenVersion)
        tx.insertRefreshToken(
            id = UuidV7.newString(),
            userId = userId,
            deviceId = deviceId,
            familyId = familyId ?: UuidV7.newString(),
            parentId = parentId,
            jtiHash = TokenHasher.sha256Hex(tokens.refreshJti),
            issuedAt = now,
            expiresAt = now.plusSeconds(config.jwt.refreshTtlDays * 24 * 3600),
        )
        return tokens
    }
}
