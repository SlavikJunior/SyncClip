package io.github.slavikjunior.syncclip.feature.auth.impl.data.remote

import kotlinx.serialization.Serializable

/** `POST /fcm/register`. */
@Serializable
internal data class FcmRegisterRequestDto(
    val deviceId: String,
    val fcmToken: String,
)
