package io.github.slavikjunior.syncclip.server.core.security

/** Пара токенов + метаданные, нужные сервису для записи refresh-строки. */
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Long,
    val refreshExpiresAt: Long,
    val refreshJti: String,
)
