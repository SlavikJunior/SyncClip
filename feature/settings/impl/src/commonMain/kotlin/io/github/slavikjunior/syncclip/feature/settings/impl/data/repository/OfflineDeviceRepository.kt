package io.github.slavikjunior.syncclip.feature.settings.impl.data.repository

import io.github.slavikjunior.syncclip.core.database.PendingDeviceUpdateDao
import io.github.slavikjunior.syncclip.core.database.PendingDeviceUpdateEntity
import io.github.slavikjunior.syncclip.feature.settings.impl.data.cache.DeviceCache
import io.github.slavikjunior.syncclip.feature.settings.impl.data.mapper.toDomain
import io.github.slavikjunior.syncclip.feature.settings.impl.data.mapper.toTransport
import io.github.slavikjunior.syncclip.feature.settings.impl.data.remote.DeviceApi
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceItem
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceKind
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class OfflineDeviceRepository(
    private val api: DeviceApi,
    private val pendingDao: PendingDeviceUpdateDao,
    private val deviceCache: DeviceCache,
) : DeviceRepository {

    override suspend fun loadDevices(): Result<List<DeviceItem>> = runCatching {
        val pending = pendingDao.observeAll().first()
        api.getDevices().getOrThrow().map { dto ->
            val update = pending.firstOrNull { it.deviceId == dto.id }
            if (update == null) {
                dto.toDomain()
            } else {
                dto.copy(
                    name = update.name ?: dto.name,
                    icon = update.icon ?: dto.icon,
                ).toDomain()
            }
        }
    }

    override suspend fun updateDevice(
        id: String,
        name: String?,
        icon: DeviceKind?,
    ): Result<DeviceItem> {
        val result = api.updateDevice(id, name, icon?.toTransport())
        if (result.isFailure) {
            pendingDao.upsert(
                PendingDeviceUpdateEntity(
                    deviceId = id,
                    name = name,
                    icon = icon?.toTransport(),
                ),
            )
            return Result.failure(
                result.exceptionOrNull() ?: IllegalStateException("Device update failed"),
            )
        }
        pendingDao.delete(id)
        // Каталог кэшируется отдельно от экрана настроек: без сброса History
        // показывал бы старое имя до истечения TTL.
        deviceCache.invalidate()
        return result.map { it.toDomain() }
    }

    override suspend fun flushPending(): Int {
        var applied = 0
        pendingDao.observeAll().first().forEach { update ->
            val result = api.updateDevice(update.deviceId, update.name, update.icon)
            if (result.isSuccess) {
                pendingDao.delete(update.deviceId)
                applied++
            }
        }
        if (applied > 0) {
            deviceCache.invalidate()
        }
        return applied
    }

    override fun observePendingCount(): Flow<Int> =
        pendingDao.observeAll().map { it.size }
}
