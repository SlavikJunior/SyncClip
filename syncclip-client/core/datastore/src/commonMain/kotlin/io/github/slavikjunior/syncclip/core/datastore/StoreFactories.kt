package io.github.slavikjunior.syncclip.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/**
 * Публичные фабрики хранилищ для DI.
 *
 * Реализации остаются `internal` (INV-26): снаружи виден только контракт.
 */
public fun createSessionStore(dataStore: DataStore<Preferences>): SessionStore =
    SessionPreferences(dataStore)

public fun createDeviceIdentityStore(dataStore: DataStore<Preferences>): DeviceIdentityStore =
    DeviceIdentityPreferences(dataStore)

public fun createClipboardSettingsStore(
    dataStore: DataStore<Preferences>,
): ClipboardSettingsStore = ClipboardSettingsPreferences(dataStore)

public fun createE2eeLockoutStore(
    dataStore: DataStore<Preferences>,
): E2eeLockoutStore = E2eeLockoutPreferences(dataStore)

public fun createE2eeKeyStore(platformContext: Any? = null): E2eeKeyStore =
    SecureE2eeKey(createE2eeKeyStorage(platformContext))
