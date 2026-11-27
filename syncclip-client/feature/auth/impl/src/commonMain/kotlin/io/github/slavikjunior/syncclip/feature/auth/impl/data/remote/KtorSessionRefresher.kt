package io.github.slavikjunior.syncclip.feature.auth.impl.data.remote

import io.github.slavikjunior.syncclip.core.datastore.SessionRefreshOutcome
import io.github.slavikjunior.syncclip.core.datastore.SessionRefresher
import io.github.slavikjunior.syncclip.core.datastore.SessionSnapshot
import io.github.slavikjunior.syncclip.core.datastore.SessionState
import io.github.slavikjunior.syncclip.core.datastore.SessionStore
import io.github.slavikjunior.syncclip.shared.network.JwtExpiry
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Обновляет пару токенов через `POST /v1/auth/refresh`.
 *
 * Сервер ротирует refresh-токен, поэтому новая пара сохраняется в DataStore —
 * иначе следующее обновление получит `refresh_token_reused`.
 *
 * Мьютекс обязателен: транспорт и фоновые запросы могут обнаружить истечение
 * одновременно, а параллельный refresh отозвал бы семью.
 */
internal class KtorSessionRefresher(
    private val client: HttpClient,
    private val session: SessionStore,
    private val clock: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) : SessionRefresher {

    private val mutex = Mutex()

    override suspend fun ensureFresh(): Boolean = mutex.withLock {
        val current = currentSession() ?: return@withLock false
        if (JwtExpiry.isFresh(current.accessToken, clock())) return@withLock true
        rotate(current.refreshToken) == SessionRefreshOutcome.REFRESHED
    }

    override suspend fun refreshAfterUnauthorized(
        staleAccessToken: String?,
    ): SessionRefreshOutcome = mutex.withLock {
        val current = currentSession() ?: return@withLock SessionRefreshOutcome.REJECTED

        // Пока 401-запрос летел, параллельный уже обновил пару: повторный
        // refresh отозвал бы семью и выкинул пользователя.
        if (staleAccessToken != null && current.accessToken != staleAccessToken) {
            return@withLock SessionRefreshOutcome.REFRESHED
        }
        rotate(current.refreshToken)
    }

    private fun currentSession(): SessionSnapshot? =
        (session.state.value as? SessionState.SignedIn)?.session

    private suspend fun rotate(refreshToken: String): SessionRefreshOutcome {
        val response = runCatching {
            client.post("v1/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequestDto(refreshToken))
            }
        }.getOrNull() ?: return SessionRefreshOutcome.UNAVAILABLE

        if (!response.status.isSuccess()) {
            // 401 — refresh отклонён (invalid/expired/reused/revoked): сессия мертва.
            // 5xx и прочее — временная ошибка, токены не трогаем.
            return if (response.status == HttpStatusCode.Unauthorized) {
                SessionRefreshOutcome.REJECTED
            } else {
                SessionRefreshOutcome.UNAVAILABLE
            }
        }

        val body = response.bodyAsText()
        val pair = runCatching { json.decodeFromString<TokenPairDto>(body) }.getOrNull()
            ?: return SessionRefreshOutcome.UNAVAILABLE

        session.updateTokens(
            accessToken = pair.accessToken,
            refreshToken = pair.refreshToken,
        )
        return SessionRefreshOutcome.REFRESHED
    }
}
