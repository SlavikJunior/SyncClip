package io.github.slavikjunior.syncclip.feature.settings.impl.data.repository

import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceItem
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceKind
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Каталог устройств, который отдаёт заданный результат и считает загрузки. */
internal class FakeDeviceRepository(
    private var response: Result<List<DeviceItem>> = Result.success(emptyList()),
) : DeviceRepository {

    var loadCalls: Int = 0
        private set

    fun respondWith(result: Result<List<DeviceItem>>) {
        response = result
    }

    override suspend fun loadDevices(): Result<List<DeviceItem>> {
        loadCalls++
        return response
    }

    override suspend fun updateDevice(
        id: String,
        name: String?,
        icon: DeviceKind?,
    ): Result<DeviceItem> = error("в тесте не используется")

    override suspend fun flushPending(): Int = 0

    override fun observePendingCount(): Flow<Int> = flowOf(0)
}
