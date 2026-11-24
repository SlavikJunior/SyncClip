package io.github.slavikjunior.syncclip.server.core.database.model

/** Просроченный гость: deviceId уникален только в паре с userId (SEC-09). */
data class ExpiredGuest(
    val userId: String,
    val deviceId: String,
)
