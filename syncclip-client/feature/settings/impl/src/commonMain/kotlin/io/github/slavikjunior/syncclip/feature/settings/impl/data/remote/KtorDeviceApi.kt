package io.github.slavikjunior.syncclip.feature.settings.impl.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody

internal class KtorDeviceApi(
    private val client: HttpClient,
) : DeviceApi {

    override suspend fun getDevices(): Result<List<DeviceDto>> = runCatching {
        client.get("devices").body()
    }

    override suspend fun updateDevice(
        id: String,
        name: String?,
        icon: String?,
    ): Result<DeviceDto> = runCatching {
        client.patch("devices/$id") {
            setBody(UpdateDeviceRequest(name = name, icon = icon))
        }.body()
    }
}
