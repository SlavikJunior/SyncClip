package io.github.slavikjunior.syncclip.server.contracts.dto.auth

import kotlinx.serialization.Serializable

/** Ответ register/login (API_SPEC): userId + пара токенов. */
@Serializable
data class AuthResponse(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
)
