package io.github.slavikjunior.syncclip.feature.auth.impl.data.remote

import kotlinx.serialization.Serializable

/** `POST /auth/firebase`: обмен Firebase ID token на JWT сервера. */
@Serializable
internal data class FirebaseLoginRequestDto(
    val idToken: String,
    val deviceId: String,
    val deviceName: String,
    val osType: String,
)
