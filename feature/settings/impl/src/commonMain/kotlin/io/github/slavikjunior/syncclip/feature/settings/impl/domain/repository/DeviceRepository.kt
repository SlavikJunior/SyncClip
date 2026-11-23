package io.github.slavikjunior.syncclip.feature.settings.impl.domain.repository

import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceItem
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceKind
import kotlinx.coroutines.flow.Flow

internal interface DeviceRepository {
    suspend fun loadDevices(): Result<List<DeviceItem>>
    suspend fun updateDevice(
        id: String,
        name: String?,
        icon: DeviceKind?,
    ): Result<DeviceItem>

    /** Повторная отправка отложенных изменений после восстановления сети. */
    suspend fun flushPending(): Int
    fun observePendingCount(): Flow<Int>
}
