package io.github.slavikjunior.syncclip.server.core.database.model

/** Последний ACK-нутый пакет устройства (FUNC-05). */
data class AckWatermark(
    val eventId: String,
    val seq: Long,
)
