package io.github.slavikjunior.syncclip.feature.settings.impl.data.remote

import kotlinx.serialization.Serializable

@Serializable
internal data class DeviceDto(
    val id: String,
    val name: String,
    val icon: String,
    val platform: String,
)
