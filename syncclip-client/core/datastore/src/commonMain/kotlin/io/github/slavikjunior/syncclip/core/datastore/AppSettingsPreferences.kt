package io.github.slavikjunior.syncclip.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Настройки Phase 2 в DataStore.
 *
 * Битый файл настроек не должен ронять приложение и не должен применять
 * невалидные значения: ошибка чтения — значения по умолчанию, множитель шрифта
 * и таймаут проверяются [AppSettingsRules].
 */
internal class AppSettingsPreferences(
    private val dataStore: DataStore<Preferences>,
) : AppSettingsStore {

    override val state: Flow<AppSettings> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences -> preferences.toSettings() }

    override suspend fun current(): AppSettings =
        runCatching { state.first() }.getOrDefault(AppSettings())

    override suspend fun setTheme(mode: String, preset: String): Boolean {
        if (!AppSettingsRules.isValidThemeMode(mode)) return false
        if (!AppSettingsRules.isValidThemePreset(preset)) return false
        dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
            preferences[THEME_PRESET] = preset
        }
        return true
    }

    override suspend fun setRandomThemeSeed(seed: Long) {
        dataStore.edit { preferences -> preferences[RANDOM_THEME_SEED] = seed }
    }

    override suspend fun setFontScale(scale: Float): Boolean {
        if (!AppSettingsRules.isValidFontScale(scale)) return false
        dataStore.edit { preferences -> preferences[FONT_SCALE] = scale }
        return true
    }

    override suspend fun setNotifications(settings: NotificationSettings) {
        dataStore.edit { preferences ->
            preferences[NOTIFY_NEW_COPIES] = settings.newCopies
            preferences[NOTIFY_NEW_DEVICE] = settings.newDevice
            preferences[NOTIFY_FAILED_SIGN_IN] = settings.failedSignIn
            preferences[NOTIFY_SOUND] = settings.sound
            preferences[NOTIFY_VIBRATION] = settings.vibration
        }
    }

    override suspend fun setAutoClearSensitive(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[AUTO_CLEAR_SENSITIVE] = enabled }
    }

    override suspend fun setBiometricLock(enabled: Boolean, timeoutMinutes: Int): Boolean {
        if (!AppSettingsRules.isValidTimeout(timeoutMinutes)) return false
        dataStore.edit { preferences ->
            preferences[BIOMETRIC_LOCK] = enabled
            preferences[BIOMETRIC_TIMEOUT_MINUTES] = timeoutMinutes
        }
        return true
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            appearance = AppearanceSettings(
                themeMode = AppSettingsRules.themeModeOr(this[THEME_MODE]),
                themePreset = AppSettingsRules.themePresetOr(this[THEME_PRESET]),
                randomThemeSeed = this[RANDOM_THEME_SEED],
                fontScale = AppSettingsRules.fontScaleOr(this[FONT_SCALE]),
            ),
            notifications = NotificationSettings(
                newCopies = this[NOTIFY_NEW_COPIES] ?: defaults.notifications.newCopies,
                newDevice = this[NOTIFY_NEW_DEVICE] ?: defaults.notifications.newDevice,
                failedSignIn = this[NOTIFY_FAILED_SIGN_IN] ?: defaults.notifications.failedSignIn,
                sound = this[NOTIFY_SOUND] ?: defaults.notifications.sound,
                vibration = this[NOTIFY_VIBRATION] ?: defaults.notifications.vibration,
            ),
            autoClearSensitive = this[AUTO_CLEAR_SENSITIVE] ?: false,
            security = SecuritySettings(
                biometricLock = this[BIOMETRIC_LOCK] ?: false,
                timeoutMinutes = AppSettingsRules.timeoutOr(this[BIOMETRIC_TIMEOUT_MINUTES]),
            ),
        )
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("appearance.theme_mode")
        val THEME_PRESET = stringPreferencesKey("appearance.theme_preset")
        val RANDOM_THEME_SEED = longPreferencesKey("appearance.random_theme_seed")
        val FONT_SCALE = floatPreferencesKey("appearance.font_scale")

        val NOTIFY_NEW_COPIES = booleanPreferencesKey("notifications.new_copies")
        val NOTIFY_NEW_DEVICE = booleanPreferencesKey("notifications.new_device")
        val NOTIFY_FAILED_SIGN_IN = booleanPreferencesKey("notifications.failed_sign_in")
        val NOTIFY_SOUND = booleanPreferencesKey("notifications.sound")
        val NOTIFY_VIBRATION = booleanPreferencesKey("notifications.vibration")

        val AUTO_CLEAR_SENSITIVE = booleanPreferencesKey("privacy.auto_clear_sensitive")

        val BIOMETRIC_LOCK = booleanPreferencesKey("security.biometric_lock")
        val BIOMETRIC_TIMEOUT_MINUTES = intPreferencesKey("security.biometric_timeout_minutes")
    }
}
