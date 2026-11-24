package io.github.slavikjunior.syncclip.server.contracts.dto.auth

import kotlinx.serialization.Serializable

/** Ответ `/auth/refresh`. */
@Serializable
data class TokenPairDto(
    val accessToken: String,
    val refreshToken: String,
)
