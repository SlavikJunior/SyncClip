package io.github.slavikjunior.syncclip.feature.settings.impl.data.repository

import io.github.slavikjunior.syncclip.core.database.PendingDeviceUpdateDao
import io.github.slavikjunior.syncclip.core.database.PendingDeviceUpdateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Очередь отложенных изменений в памяти. */
internal class FakePendingDeviceUpdateDao(
    initial: List<PendingDeviceUpdateEntity> = emptyList(),
) : PendingDeviceUpdateDao {

    private val updates = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<PendingDeviceUpdateEntity>> = updates.asStateFlow()

    override suspend fun upsert(entity: PendingDeviceUpdateEntity) {
        updates.value = updates.value.filterNot { it.deviceId == entity.deviceId } + entity
    }

    override suspend fun delete(deviceId: String) {
        updates.value = updates.value.filterNot { it.deviceId == deviceId }
    }
}
