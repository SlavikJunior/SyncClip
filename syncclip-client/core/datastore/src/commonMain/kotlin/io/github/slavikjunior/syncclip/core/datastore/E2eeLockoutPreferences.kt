package io.github.slavikjunior.syncclip.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Блокировка мастер-пароля в DataStore.
 *
 * Повреждённые/отсутствующие значения означают «блокировки нет»: приложение не
 * должно запереть пользователя из-за битого файла настроек.
 */
internal class E2eeLockoutPreferences(
    private val dataStore: DataStore<Preferences>,
) : E2eeLockoutStore {

    override val state: Flow<MasterPasswordLockout> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            MasterPasswordLockout(
                remainingAttempts = preferences[REMAINING]
                    ?: MasterPasswordLockout.ATTEMPTS_PER_GROUP,
                lockedUntilMillis = preferences[LOCKED_UNTIL] ?: 0L,
            )
        }

    override suspend fun current(): MasterPasswordLockout =
        runCatching { state.first() }.getOrDefault(MasterPasswordLockout())

    override suspend fun recordAttempt(remaining: Int) {
        dataStore.edit { preferences ->
            preferences[REMAINING] = remaining.coerceAtLeast(0)
            preferences[LOCKED_UNTIL] = 0L
        }
    }

    override suspend fun lockUntil(lockedUntilMillis: Long) {
        dataStore.edit { preferences ->
            preferences[REMAINING] = 0
            preferences[LOCKED_UNTIL] = lockedUntilMillis
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences[REMAINING] = MasterPasswordLockout.ATTEMPTS_PER_GROUP
            preferences[LOCKED_UNTIL] = 0L
        }
    }

    private companion object {
        val REMAINING = intPreferencesKey("e2ee.remaining_attempts")
        val LOCKED_UNTIL = longPreferencesKey("e2ee.locked_until")
    }
}
