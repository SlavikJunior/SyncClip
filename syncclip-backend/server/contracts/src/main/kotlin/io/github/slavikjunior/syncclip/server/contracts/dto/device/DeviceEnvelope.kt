package io.github.slavikjunior.syncclip.server.contracts.dto.device

import kotlinx.serialization.Serializable

@Serializable
data class DeviceEnvelope(val device: DeviceDto)
