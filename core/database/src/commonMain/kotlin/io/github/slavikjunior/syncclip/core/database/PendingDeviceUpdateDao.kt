package io.github.slavikjunior.syncclip.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingDeviceUpdateDao {

    @Query("SELECT * FROM pending_device_updates")
    fun observeAll(): Flow<List<PendingDeviceUpdateEntity>>

    @Upsert
    suspend fun upsert(entity: PendingDeviceUpdateEntity)

    @Query("DELETE FROM pending_device_updates WHERE deviceId = :deviceId")
    suspend fun delete(deviceId: String)
}
