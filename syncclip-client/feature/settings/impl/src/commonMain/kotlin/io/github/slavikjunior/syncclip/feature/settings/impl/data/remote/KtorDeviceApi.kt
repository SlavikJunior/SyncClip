package io.github.slavikjunior.syncclip.feature.settings.impl.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal class KtorDeviceApi(
    private val client: HttpClient,
) : DeviceApi {

    override suspend fun getDevices(): Result<List<DeviceDto>> = runCatching {
        client.get("v1/devices").body<DeviceListDto>().devices
    }

    override suspend fun updateDevice(
        id: String,
        name: String?,
        icon: String?,
    ): Result<DeviceDto> = runCatching {
        client.patch("v1/devices/$id") {
            contentType(ContentType.Application.Json)
            setBody(UpdateDeviceRequest(name = name, icon = icon))
        }.body<DeviceEnvelopeDto>().device
    }
}
