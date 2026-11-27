package io.github.slavikjunior.syncclip.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class ClipboardSettingsPreferencesTest {

    private val dataStore = InMemoryPreferencesDataStore()
    private val store = createClipboardSettingsStore(dataStore)

    @Test
    fun `defaults are online with auto insert`() = runTest {
        assertEquals(ClipboardSettings(isOnline = true, autoInsert = true), store.current())
    }

    @Test
    fun `online toggle is persisted`() = runTest {
        store.setOnline(false)

        assertFalse(store.current().isOnline)
        assertEquals(
            ClipboardSettings(isOnline = false, autoInsert = true),
            store.state.first(),
        )
    }

    @Test
    fun `auto insert toggle is persisted independently`() = runTest {
        store.setAutoInsert(false)
        store.setOnline(false)
        store.setAutoInsert(true)

        assertEquals(
            ClipboardSettings(isOnline = false, autoInsert = true),
            store.current(),
        )
    }

    @Test
    fun `history defaults match the passport`() = runTest {
        val settings = store.current()

        assertEquals(10, settings.maxEntries)
        assertEquals(7 * 24, settings.ttlHours)
        assertEquals(10L * 1024 * 1024, settings.maxBytes)
    }

    @Test
    fun `history limits are persisted atomically`() = runTest {
        assertTrue(store.setHistoryLimits(maxEntries = 50, ttlHours = 48, maxBytes = 5L * 1024 * 1024))

        val settings = store.current()
        assertEquals(50, settings.maxEntries)
        assertEquals(48, settings.ttlHours)
        assertEquals(5L * 1024 * 1024, settings.maxBytes)
        assertEquals(settings, store.state.first())
    }

    @Test
    fun `history limits outside ranges are rejected`() = runTest {
        assertFalse(store.setHistoryLimits(maxEntries = 4, ttlHours = 48, maxBytes = 5L * 1024 * 1024))
        assertFalse(store.setHistoryLimits(maxEntries = 50, ttlHours = 0, maxBytes = 5L * 1024 * 1024))
        assertFalse(store.setHistoryLimits(maxEntries = 50, ttlHours = 48, maxBytes = 512 * 1024L))
        assertFalse(store.setMaxEntries(201))
        assertFalse(store.setTtlHours(721))
        assertFalse(store.setMaxBytes(101L * 1024 * 1024))

        assertEquals(ClipboardSettings(), store.current())
    }

    @Test
    fun `each history limit can be changed separately`() = runTest {
        assertTrue(store.setMaxEntries(200))
        assertTrue(store.setTtlHours(1))
        assertTrue(store.setMaxBytes(100L * 1024 * 1024))

        val settings = store.current()
        assertEquals(200, settings.maxEntries)
        assertEquals(1, settings.ttlHours)
        assertEquals(100L * 1024 * 1024, settings.maxBytes)
    }

    @Test
    fun `corrupted file falls back to defaults instead of throwing`() = runTest {
        val broken = CorruptedPreferencesDataStore()

        val settings = createClipboardSettingsStore(broken).current()

        assertEquals(ClipboardSettings(), settings)
    }

    /**
     * Валидация на записи не спасает от повреждённого файла: `ttlHours = 0`
     * сделал бы cutoff в будущем, и вытеснение удалило бы всю историю.
     */
    @Test
    fun `invalid limits read from disk are replaced by defaults`() = runTest {
        val raw = InMemoryPreferencesDataStore()
        raw.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[intPreferencesKey("history.max_entries")] = -5
                this[intPreferencesKey("history.ttl_hours")] = 0
                this[longPreferencesKey("history.max_bytes")] = -1L
            }
        }

        val settings = createClipboardSettingsStore(raw).current()

        assertEquals(ClipboardSettings.DEFAULT_MAX_ENTRIES, settings.maxEntries)
        assertEquals(ClipboardSettings.DEFAULT_TTL_HOURS, settings.ttlHours)
        assertEquals(ClipboardSettings.DEFAULT_MAX_BYTES, settings.maxBytes)
    }
}

/** DataStore, который бросает при чтении — как повреждённый файл настроек. */
private class CorruptedPreferencesDataStore : DataStore<Preferences> {

    override val data: Flow<Preferences> = kotlinx.coroutines.flow.flow {
        throw IllegalStateException("corrupted preferences")
    }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = error("cannot write")
}

/** Минимальная замена DataStore для unit-тестов: те же контракты, без файлов. */
internal class InMemoryPreferencesDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {

    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
