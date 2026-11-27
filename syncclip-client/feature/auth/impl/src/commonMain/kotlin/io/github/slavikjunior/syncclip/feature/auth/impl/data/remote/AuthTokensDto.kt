package io.github.slavikjunior.syncclip.feature.auth.impl.data.remote

import kotlinx.serialization.Serializable

/** Ответ `/auth/firebase`: пара токенов сервера. */
@Serializable
internal data class AuthTokensDto(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
)
