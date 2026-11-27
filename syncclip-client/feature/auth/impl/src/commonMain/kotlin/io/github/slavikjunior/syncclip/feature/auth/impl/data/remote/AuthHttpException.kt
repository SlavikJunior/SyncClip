package io.github.slavikjunior.syncclip.feature.auth.impl.data.remote

/** HTTP-отказ сервера с машиночитаемым кодом (`invalid_credentials`, …). */
internal class AuthHttpException(
    val statusCode: Int,
    val errorCode: String?,
) : Exception("Auth request failed: $statusCode ${errorCode.orEmpty()}")
