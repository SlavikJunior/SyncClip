package io.github.slavikjunior.syncclip.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/**
 * Идентичность устройства в DataStore.
 *
 * `deviceId` создаётся ровно один раз: параллельные вызовы [identity]
 * сериализуются мьютексом, иначе два экрана сгенерировали бы разные UUID и
 * сервер увидел бы два устройства.
 */
internal class DeviceIdentityPreferences(
    private val dataStore: DataStore<Preferences>,
    private val defaultName: () -> String = ::platformDeviceName,
    private val osType: () -> String = ::platformOsType,
) : DeviceIdentityStore {

    private val mutex = Mutex()
    private var cached: DeviceIdentity? = null

    override suspend fun identity(): DeviceIdentity = mutex.withLock {
        cached?.let { return it }

        val preferences = runCatching { dataStore.data.first() }.getOrNull()
        val storedId = preferences?.get(DEVICE_ID)
        val deviceId = storedId ?: Uuid.random().toString().also { generated ->
            dataStore.edit { it[DEVICE_ID] = generated }
        }
        val name = preferences?.get(DEVICE_NAME) ?: defaultName()

        DeviceIdentity(
            deviceId = deviceId,
            deviceName = name,
            osType = osType(),
        ).also {
            cached = it
            // Share Extension подписывает WS тем же deviceId (claim `dev`).
            shareBridge().device(deviceId)
        }
    }

    private companion object {
        val DEVICE_ID = stringPreferencesKey("device.id")
        val DEVICE_NAME = stringPreferencesKey("device.name")
    }
}
