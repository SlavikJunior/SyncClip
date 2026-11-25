package io.github.slavikjunior.syncclip.feature.settings.impl.data.remote

import kotlinx.serialization.Serializable

@Serializable
internal data class UpdateDeviceRequest(
    val name: String? = null,
    val icon: String? = null,
)
