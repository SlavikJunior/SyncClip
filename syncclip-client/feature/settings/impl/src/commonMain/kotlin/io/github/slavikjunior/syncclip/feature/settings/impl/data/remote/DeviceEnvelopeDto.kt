package io.github.slavikjunior.syncclip.feature.settings.impl.data.remote

import kotlinx.serialization.Serializable

/** Ответ `PATCH /v1/devices/{id}`. */
@Serializable
internal data class DeviceEnvelopeDto(val device: DeviceDto)
