package io.github.slavikjunior.syncclip.core.clipboard

/**
 * Принятое локальное копирование: то, что пользователь действительно скопировал
 * сам. Программные вставки и дубли сюда не попадают (FR-2.7, FR-2.8).
 */
public data class ClipboardCapture(
    val text: String,
    val capturedAtMillis: Long,
)
