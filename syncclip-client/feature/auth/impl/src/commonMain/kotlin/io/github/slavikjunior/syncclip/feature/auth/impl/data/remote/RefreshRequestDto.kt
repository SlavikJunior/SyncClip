package io.github.slavikjunior.syncclip.feature.auth.impl.data.remote

import kotlinx.serialization.Serializable

/** `POST /v1/auth/refresh`: текущий refresh-токен сервера. */
@Serializable
internal data class RefreshRequestDto(val refreshToken: String)
