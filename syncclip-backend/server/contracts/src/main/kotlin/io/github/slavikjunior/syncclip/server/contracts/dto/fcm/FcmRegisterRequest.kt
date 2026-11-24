package io.github.slavikjunior.syncclip.server.contracts.dto.fcm

import kotlinx.serialization.Serializable

@Serializable
data class FcmRegisterRequest(
    val deviceId: String,
    val fcmToken: String,
)
