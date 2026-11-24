package io.github.slavikjunior.syncclip.server.feature.notifications.domain

import io.github.slavikjunior.syncclip.server.contracts.dto.notification.NotificationDto
import io.github.slavikjunior.syncclip.server.contracts.dto.notification.UnreadNotificationsResponse
import io.github.slavikjunior.syncclip.server.contracts.error.ErrorCode
import io.github.slavikjunior.syncclip.server.contracts.error.ForbiddenException
import io.github.slavikjunior.syncclip.server.contracts.model.AuthContext
import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.github.slavikjunior.syncclip.server.core.database.model.AttemptKind
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.listFailedAttempts
import io.github.slavikjunior.syncclip.server.core.database.tx
import java.time.Duration
import java.time.Instant

/**
 * Производная лента уведомлений (CG-04): неудачные попытки login/мастер-пароля.
 * «Прочитано» — клиентский курсор `since`, сервер состояние не хранит.
 *
 * Гость ленту не читает: она содержит чужие IP/UA и security-события
 * владельца (SEC-02).
 */
class UnreadFeedService(
    private val databaseFactory: DatabaseFactory,
    private val clock: () -> Instant = Instant::now,
) {

    suspend fun unread(context: AuthContext, since: String?, limit: Int): UnreadNotificationsResponse {
        if (context.isGuest) throw ForbiddenException(ErrorCode.FORBIDDEN_FOR_GUEST)
        val now = clock()
        val cursor = parseCursor(since)
        val pageSize = limit.coerceIn(1, MAX_LIMIT)
        val attempts = tx(databaseFactory.database) {
            listFailedAttempts(
                userId = context.userId,
                kinds = listOf(AttemptKind.MASTER_PASSWORD, AttemptKind.LOGIN),
                notBefore = now.minus(RETENTION),
                before = cursor?.at ?: now,
                beforeId = cursor?.id,
                limit = pageSize,
            )
        }
        val items = attempts.map { attempt ->
            NotificationDto(
                id = attempt.id,
                type = attempt.kind,
                createdAt = attempt.attemptedAt.toString(),
                ip = attempt.ipAddress,
                deviceModel = attempt.deviceModel,
                userAgent = attempt.userAgent,
            )
        }
        val last = attempts.lastOrNull()
        return UnreadNotificationsResponse(
            items = items,
            // Непрозрачный keyset-курсор: клиент возвращает его как `since`.
            nextCursor = last?.let { "${it.attemptedAt}|${it.id}" },
        )
    }

    /**
     * Поддерживает `(attemptedAt, id)` и legacy-курсор только с временем
     * (ISO-8601 или epoch millis).
     */
    private fun parseCursor(raw: String?): Cursor? {
        if (raw.isNullOrBlank()) return null
        val value = raw.trim()
        val id = if ('|' in value) value.substringAfterLast('|').takeIf { it.isNotBlank() } else null
        val timePart = if (id != null) value.substringBeforeLast('|') else value
        val at = runCatching { Instant.parse(timePart) }.getOrNull()
            ?: timePart.toLongOrNull()?.let { Instant.ofEpochMilli(it) }
            ?: return null
        return Cursor(at, id)
    }

    private data class Cursor(val at: Instant, val id: String?)

    private companion object {
        val RETENTION: Duration = Duration.ofDays(7)
        const val MAX_LIMIT = 100
    }
}
