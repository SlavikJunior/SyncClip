package io.github.slavikjunior.syncclip.core.datastore

import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class AppSettingsPreferencesTest {

    private val dataStore = InMemoryPreferencesDataStore()
    private val store = createAppSettingsStore(dataStore)

    @Test
    fun `defaults follow the phase 2 specification`() = runTest {
        val settings = store.current()

        assertEquals("SYSTEM", settings.appearance.themeMode)
        assertEquals("AQUA", settings.appearance.themePreset)
        assertEquals(1.0f, settings.appearance.fontScale)
        // US-009: уведомления о новых копиях по умолчанию выключены.
        assertFalse(settings.notifications.newCopies)
        assertTrue(settings.notifications.newDevice)
        assertTrue(settings.notifications.failedSignIn)
        assertTrue(settings.notifications.sound)
        assertTrue(settings.notifications.vibration)
        // US-021: автоочистка чувствительных данных выключена.
        assertFalse(settings.autoClearSensitive)
        // US-022: блокировка выключена, таймаут — 5 минут.
        assertFalse(settings.security.biometricLock)
        assertEquals(5, settings.security.timeoutMinutes)
    }

    @Test
    fun `theme and seed are persisted`() = runTest {
        assertTrue(store.setTheme(mode = "DARK", preset = "RANDOM"))
        store.setRandomThemeSeed(0x1234L)

        val appearance = store.current().appearance
        assertEquals("DARK", appearance.themeMode)
        assertEquals("RANDOM", appearance.themePreset)
        assertEquals(0x1234L, appearance.randomThemeSeed)
        assertEquals(appearance, store.state.first().appearance)
    }

    @Test
    fun `unknown theme values are rejected on write`() = runTest {
        assertFalse(store.setTheme(mode = "NEON", preset = "AQUA"))
        assertFalse(store.setTheme(mode = "SYSTEM", preset = "NEON"))
        assertEquals("SYSTEM", store.current().appearance.themeMode)
    }

    @Test
    fun `font scale outside the four steps is rejected`() = runTest {
        assertFalse(store.setFontScale(0f))
        assertTrue(store.setFontScale(1.3f))
        assertEquals(1.3f, store.current().appearance.fontScale)
    }

    @Test
    fun `notification flags are stored independently`() = runTest {
        store.setNotifications(
            NotificationSettings(
                newCopies = true,
                newDevice = false,
                failedSignIn = false,
                sound = false,
                vibration = true,
            ),
        )

        val notifications = store.current().notifications
        assertTrue(notifications.newCopies)
        assertFalse(notifications.newDevice)
        assertFalse(notifications.failedSignIn)
        assertFalse(notifications.sound)
        assertTrue(notifications.vibration)
    }

    @Test
    fun `biometric lock and timeout are persisted`() = runTest {
        assertTrue(store.setBiometricLock(enabled = true, timeoutMinutes = 0))
        assertTrue(store.current().security.biometricLock)
        assertEquals(0, store.current().security.timeoutMinutes)

        assertFalse(store.setBiometricLock(enabled = true, timeoutMinutes = 999))
        assertEquals(0, store.current().security.timeoutMinutes)
    }

    @Test
    fun `corrupted file falls back to defaults`() = runTest {
        val broken = object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
            override val data: Flow<androidx.datastore.preferences.core.Preferences> = flow {
                throw IllegalStateException("corrupted preferences")
            }

            override suspend fun updateData(
                transform: suspend (t: androidx.datastore.preferences.core.Preferences) ->
                androidx.datastore.preferences.core.Preferences,
            ): androidx.datastore.preferences.core.Preferences = error("cannot write")
        }

        assertEquals(AppSettings(), createAppSettingsStore(broken).current())
    }

    @Test
    fun `invalid values from disk are replaced by defaults`() = runTest {
        val raw = InMemoryPreferencesDataStore()
        raw.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[stringPreferencesKey("appearance.theme_mode")] = "NEON"
                this[floatPreferencesKey("appearance.font_scale")] = 0f
                this[intPreferencesKey("security.biometric_timeout_minutes")] = -7
            }
        }

        val settings = createAppSettingsStore(raw).current()

        assertEquals("SYSTEM", settings.appearance.themeMode)
        assertEquals(AppearanceSettings.DEFAULT_FONT_SCALE, settings.appearance.fontScale)
        assertEquals(SecuritySettings.DEFAULT_TIMEOUT_MINUTES, settings.security.timeoutMinutes)
    }
}
