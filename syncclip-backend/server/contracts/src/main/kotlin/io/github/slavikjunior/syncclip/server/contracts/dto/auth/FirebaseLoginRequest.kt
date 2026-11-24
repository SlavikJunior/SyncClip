package io.github.slavikjunior.syncclip.server.contracts.dto.auth

import kotlinx.serialization.Serializable

/** Additive Firebase Auth: клиент передаёт Firebase ID token (SEC-10). */
@Serializable
data class FirebaseLoginRequest(
    val idToken: String,
    val deviceId: String,
    val deviceName: String,
    val osType: String,
)
