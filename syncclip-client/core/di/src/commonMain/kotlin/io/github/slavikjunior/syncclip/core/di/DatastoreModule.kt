package io.github.slavikjunior.syncclip.core.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.github.slavikjunior.syncclip.core.datastore.AppSettingsStore
import io.github.slavikjunior.syncclip.core.datastore.AutoStart
import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettingsStore
import io.github.slavikjunior.syncclip.core.datastore.DeviceIdentityStore
import io.github.slavikjunior.syncclip.core.datastore.E2eeLockoutStore
import io.github.slavikjunior.syncclip.core.datastore.E2eeKeyStore
import io.github.slavikjunior.syncclip.core.datastore.SessionStore
import io.github.slavikjunior.syncclip.core.datastore.createAppSettingsStore
import io.github.slavikjunior.syncclip.core.datastore.createAutoStart
import io.github.slavikjunior.syncclip.core.datastore.createClipboardSettingsStore
import io.github.slavikjunior.syncclip.core.datastore.createDeviceIdentityStore
import io.github.slavikjunior.syncclip.core.datastore.createE2eeLockoutStore
import io.github.slavikjunior.syncclip.core.datastore.createE2eeKeyStore
import io.github.slavikjunior.syncclip.core.datastore.createPreferencesDataStore
import io.github.slavikjunior.syncclip.core.datastore.createSessionStore
import io.github.slavikjunior.syncclip.shared.crypto.E2eeKeyProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Хранилище настроек и сессии.
 *
 * [platformContext] — Android `Context`; на iOS/Desktop `null`, путь строится
 * от каталога приложения.
 */
public fun datastoreModule(platformContext: Any? = null): Module = module {
    single<DataStore<Preferences>> { createPreferencesDataStore(platformContext) }
    single<SessionStore> { createSessionStore(get()) }
    single<DeviceIdentityStore> { createDeviceIdentityStore(get()) }
    single<ClipboardSettingsStore> { createClipboardSettingsStore(get()) }
    // Phase 2: тема, шрифт, уведомления, приватность, биометрия (US-009…US-022).
    single<AppSettingsStore> { createAppSettingsStore(get()) }
    single<E2eeKeyStore> { createE2eeKeyStore(platformContext) }
    single<E2eeKeyProvider> { get<E2eeKeyStore>() }
    single<AutoStart> { createAutoStart(get(), platformContext) }
    single<E2eeLockoutStore> { createE2eeLockoutStore(get()) }
}
