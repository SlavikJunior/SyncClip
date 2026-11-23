package io.github.slavikjunior.syncclip.feature.settings.impl.data.repository

import io.github.slavikjunior.syncclip.feature.settings.impl.data.remote.DeviceApi
import io.github.slavikjunior.syncclip.feature.settings.impl.data.remote.DeviceDto

/** Сервер устройств, результат которого задаёт тест. */
internal class FakeDeviceApi(
    private var updateResult: Result<DeviceDto> = Result.success(dto()),
) : DeviceApi {

    var updateCalls: Int = 0
        private set

    fun respondWith(result: Result<DeviceDto>) {
        updateResult = result
    }

    override suspend fun getDevices(): Result<List<DeviceDto>> = Result.success(listOf(dto()))

    override suspend fun updateDevice(id: String, name: String?, icon: String?): Result<DeviceDto> {
        updateCalls++
        return updateResult
    }

    companion object {
        fun dto(id: String = "id-1"): DeviceDto = DeviceDto(
            id = id,
            name = "laptop",
            icon = "LAPTOP",
            platform = "ANDROID",
        )
    }
}
