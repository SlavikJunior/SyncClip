package io.github.slavikjunior.syncclip.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Тумблеры синхронизации и лимиты истории в DataStore.
 *
 * Повреждённый файл настроек не должен блокировать синхронизацию, поэтому
 * ошибка чтения трактуется как значения по умолчанию.
 */
internal class ClipboardSettingsPreferences(
    private val dataStore: DataStore<Preferences>,
) : ClipboardSettingsStore {

    override val state: Flow<ClipboardSettings> = dataStore.data
        // Битый файл настроек (краш при записи, «нет места», ручная правка) не
        // должен ронять коллекторов: `viewModelScope.launch { state.collect }`
        // на Android убил бы процесс, а транспорты перезапускались бы вечно.
        // Как у [E2eeLockoutPreferences]: считаем, что настроек нет.
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            ClipboardSettings(
                isOnline = preferences[IS_ONLINE] ?: true,
                autoInsert = preferences[AUTO_INSERT] ?: true,
                // Лимиты проверяются и на чтении: отрицательный `ttlHours` из
                // повреждённого файла сделал бы cutoff в будущем, и вытеснение
                // стёрло бы всю историю; невалидные байты — то же самое.
                maxEntries = preferences[MAX_ENTRIES]
                    ?.takeIf(HistoryLimitsRules::isValidEntries)
                    ?: ClipboardSettings.DEFAULT_MAX_ENTRIES,
                ttlHours = preferences[TTL_HOURS]
                    ?.takeIf(HistoryLimitsRules::isValidTtlHours)
                    ?: ClipboardSettings.DEFAULT_TTL_HOURS,
                maxBytes = preferences[MAX_BYTES]
                    ?.takeIf(HistoryLimitsRules::isValidBytes)
                    ?: ClipboardSettings.DEFAULT_MAX_BYTES,
            )
        }

    override suspend fun current(): ClipboardSettings =
        runCatching { state.first() }.getOrDefault(ClipboardSettings())

    override suspend fun setOnline(isOnline: Boolean) {
        dataStore.edit { preferences -> preferences[IS_ONLINE] = isOnline }
    }

    override suspend fun setAutoInsert(autoInsert: Boolean) {
        dataStore.edit { preferences -> preferences[AUTO_INSERT] = autoInsert }
    }

    override suspend fun setMaxEntries(value: Int): Boolean {
        if (!HistoryLimitsRules.isValidEntries(value)) return false
        dataStore.edit { preferences -> preferences[MAX_ENTRIES] = value }
        return true
    }

    override suspend fun setTtlHours(value: Int): Boolean {
        if (!HistoryLimitsRules.isValidTtlHours(value)) return false
        dataStore.edit { preferences -> preferences[TTL_HOURS] = value }
        return true
    }

    override suspend fun setMaxBytes(value: Long): Boolean {
        if (!HistoryLimitsRules.isValidBytes(value)) return false
        dataStore.edit { preferences -> preferences[MAX_BYTES] = value }
        return true
    }

    override suspend fun setHistoryLimits(
        maxEntries: Int,
        ttlHours: Int,
        maxBytes: Long,
    ): Boolean {
        if (!HistoryLimitsRules.validate(maxEntries, ttlHours, maxBytes)) return false
        dataStore.edit { preferences ->
            preferences[MAX_ENTRIES] = maxEntries
            preferences[TTL_HOURS] = ttlHours
            preferences[MAX_BYTES] = maxBytes
        }
        return true
    }

    private companion object {
        val IS_ONLINE = booleanPreferencesKey("clipboard.is_online")
        val AUTO_INSERT = booleanPreferencesKey("clipboard.auto_insert")
        val MAX_ENTRIES = intPreferencesKey("history.max_entries")
        val TTL_HOURS = intPreferencesKey("history.ttl_hours")
        val MAX_BYTES = longPreferencesKey("history.max_bytes")
    }
}
