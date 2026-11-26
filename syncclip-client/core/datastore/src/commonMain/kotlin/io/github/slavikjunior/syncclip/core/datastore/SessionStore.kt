package io.github.slavikjunior.syncclip.core.datastore

import kotlinx.coroutines.flow.StateFlow

/**
 * Хранилище сессии сервера.
 *
 * [accessToken] — синхронный доступ для заголовка `Authorization`: Ktor
 * `defaultRequest` не suspending, а значение уже прогрето [refresh] при старте.
 */
interface SessionStore {

    val state: StateFlow<SessionState>

    suspend fun refresh()

    suspend fun save(session: SessionSnapshot)

    /** Ротация пары после `POST /v1/auth/refresh`; userId берётся из текущей сессии. */
    suspend fun updateTokens(accessToken: String, refreshToken: String)

    suspend fun clear()

    fun accessToken(): String?
}
