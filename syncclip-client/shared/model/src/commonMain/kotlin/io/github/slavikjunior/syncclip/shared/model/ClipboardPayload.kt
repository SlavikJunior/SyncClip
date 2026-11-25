package io.github.slavikjunior.syncclip.shared.model

/**
 * Значение буфера, которым обмениваются фичи. Не содержит полей БД и не
 * сериализуется: DTO живёт в слое `data` фичи (`INV-07`).
 */
data class ClipboardPayload(
    val text: String,
    val copiedAtMillis: Long,
    val sourceDeviceId: String? = null,
    val isProgrammatic: Boolean = false,
)
