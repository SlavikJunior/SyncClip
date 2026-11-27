package io.github.slavikjunior.syncclip.feature.auth.impl.data.remote

import kotlinx.serialization.Serializable

/** Ответ refresh: сервер ротирует обе части пары. */
@Serializable
internal data class TokenPairDto(
    val accessToken: String,
    val refreshToken: String,
)
