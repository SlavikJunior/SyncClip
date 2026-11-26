package io.github.slavikjunior.syncclip.core.clipboard

/**
 * Изменение системного буфера, полученное от платформы.
 *
 * [changedAtMillis] — время изменения по часам платформы, если она его знает.
 * Android и iOS не сообщают точное время копирования, поэтому обработчик
 * подставляет момент получения события.
 */
public data class ClipboardChange(
    val text: String,
    val changedAtMillis: Long,
)
