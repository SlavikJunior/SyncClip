package io.github.slavikjunior.syncclip.feature.settings.impl.data.remote

internal interface DeviceApi {
    suspend fun getDevices(): Result<List<DeviceDto>>
    suspend fun updateDevice(
        id: String,
        name: String?,
        icon: String?,
    ): Result<DeviceDto>
}
