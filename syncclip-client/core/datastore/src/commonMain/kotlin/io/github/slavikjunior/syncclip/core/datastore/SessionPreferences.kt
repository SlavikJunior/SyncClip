package io.github.slavikjunior.syncclip.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Сессия сервера в DataStore.
 *
 * `IOException` при чтении не пробрасывается наружу: повреждённый/недоступный
 * файл означает «пользователь не авторизован», а не падение приложения.
 */
internal class SessionPreferences(
    private val dataStore: DataStore<Preferences>,
) : SessionStore {

    private val currentState = MutableStateFlow<SessionState>(SessionState.Loading)

    override val state: StateFlow<SessionState> = currentState.asStateFlow()

    override fun accessToken(): String? =
        (currentState.value as? SessionState.SignedIn)?.session?.accessToken

    override suspend fun refresh() {
        val state = read()
        currentState.value = state
        // iOS Share Extension читает access-токен из App Group: зеркалим и при
        // чтении, иначе сессия, сохранённая до появления расширения, осталась
        // бы невидимой.
        val session = (state as? SessionState.SignedIn)?.session
        shareBridge().session(session?.userId, session?.accessToken)
    }

    override suspend fun save(session: SessionSnapshot) {
        dataStore.edit { preferences ->
            preferences[USER_ID] = session.userId
            preferences[ACCESS_TOKEN] = session.accessToken
            preferences[REFRESH_TOKEN] = session.refreshToken
        }
        currentState.value = SessionState.SignedIn(session)
        shareBridge().session(session.userId, session.accessToken)
    }

    override suspend fun updateTokens(accessToken: String, refreshToken: String) {
        val current = (currentState.value as? SessionState.SignedIn) ?: return
        val updated = current.session.copy(
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
        dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN] = accessToken
            preferences[REFRESH_TOKEN] = refreshToken
        }
        currentState.value = SessionState.SignedIn(updated)
        shareBridge().session(updated.userId, updated.accessToken)
    }

    override suspend fun clear() {
        dataStore.edit { preferences -> preferences.clear() }
        currentState.value = SessionState.SignedOut
        shareBridge().session(null, null)
    }

    private suspend fun read(): SessionState = runCatching {
        val preferences = dataStore.data.first()
        val userId = preferences[USER_ID]
        val accessToken = preferences[ACCESS_TOKEN]
        val refreshToken = preferences[REFRESH_TOKEN]
        if (userId == null || accessToken == null || refreshToken == null) {
            SessionState.SignedOut
        } else {
            SessionState.SignedIn(
                SessionSnapshot(
                    userId = userId,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                ),
            )
        }
    }.getOrDefault(SessionState.SignedOut)

    private companion object {
        val USER_ID = stringPreferencesKey("session.user_id")
        val ACCESS_TOKEN = stringPreferencesKey("session.access_token")
        val REFRESH_TOKEN = stringPreferencesKey("session.refresh_token")
    }
}
