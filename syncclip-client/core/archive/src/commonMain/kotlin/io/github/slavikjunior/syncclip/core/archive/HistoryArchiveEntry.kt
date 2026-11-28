package io.github.slavikjunior.syncclip.core.archive

/** Формат файла архива истории. */
public enum class HistoryArchiveFormat(
    public val extension: String,
    public val mimeType: String,
) {
    JSON("json", "application/json"),
    CSV("csv", "text/csv"),
}

/** Одна запись архива — плоская модель без зависимости от Room и домена фичи. */
public data class HistoryArchiveEntry(
    val id: String,
    val text: String,
    val timestampMillis: Long,
    val deviceName: String?,
    val isPinned: Boolean,
    val isFavorite: Boolean,
)

/** Причина отказа разбора файла — UI показывает локализованное сообщение. */
public enum class ArchiveError {
    /** Структура не соответствует формату (нет `entries`, неизвестная версия, битый CSV). */
    INVALID_FORMAT,

    /** Файл пуст или не содержит ни одной записи. */
    EMPTY,
}

/** Итог импорта: сколько записей добавлено и сколько пропущено как дубли. */
public data class ArchiveImportResult(
    val imported: Int,
    val skipped: Int,
)

/** Предел экспорта: защита от OOM и гигантских файлов (US-018). */
public const val ARCHIVE_ENTRY_LIMIT: Int = 1_000
