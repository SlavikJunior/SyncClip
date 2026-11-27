package io.github.slavikjunior.syncclip.feature.settings.impl.data.remote

import kotlinx.serialization.Serializable

/** `GET /v1/devices` отдаёт конверт, а не голый массив. */
@Serializable
internal data class DeviceListDto(val devices: List<DeviceDto> = emptyList())
