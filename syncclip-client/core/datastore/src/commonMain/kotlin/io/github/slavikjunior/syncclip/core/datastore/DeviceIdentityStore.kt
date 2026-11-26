package io.github.slavikjunior.syncclip.core.datastore

/**
 * Идентичность текущего устройства; создаётся при первом обращении.
 */
interface DeviceIdentityStore {
    suspend fun identity(): DeviceIdentity
}
