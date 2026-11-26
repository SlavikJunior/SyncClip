package io.github.slavikjunior.syncclip.core.datastore

/**
 * Пара токенов сервера SyncClip.
 *
 * Firebase ID token живёт минуту и нужен только для `POST /auth/firebase`;
 * дальше клиент работает с access/refresh JWT сервера.
 */
data class SessionSnapshot(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
)
