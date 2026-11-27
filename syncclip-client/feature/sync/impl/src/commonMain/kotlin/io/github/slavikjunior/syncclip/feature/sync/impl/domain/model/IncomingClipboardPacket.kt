package io.github.slavikjunior.syncclip.feature.sync.impl.domain.model

/**
 * Входящий пакет буфера от другого устройства — до сохранения и вставки.
 *
 * Отдельный тип от сетевого DTO (INV-07): транспорт маппит его в эту модель.
 */
internal data class IncomingClipboardPacket(
    val eventId: String,
    val originDeviceId: String,
    val text: String,
    val copiedAtMillis: Long,
)
