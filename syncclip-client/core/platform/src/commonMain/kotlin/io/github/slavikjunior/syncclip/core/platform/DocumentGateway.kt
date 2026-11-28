package io.github.slavikjunior.syncclip.core.platform

/** Итог сохранения файла пользователем. */
public sealed interface FileSaveResult {

    /** Файл сохранён; [location] — человекочитаемое место для сообщения в UI. */
    public data class Saved(val location: String?) : FileSaveResult

    /** Пользователь закрыл системный диалог. */
    public data object Cancelled : FileSaveResult

    /** Диалог недоступен или запись не удалась. */
    public data object Failed : FileSaveResult
}

/** Итог выбора файла для импорта. */
public sealed interface FileOpenResult {

    public data class Opened(val name: String, val content: String) : FileOpenResult

    public data object Cancelled : FileOpenResult

    /** Файл недоступен, пуст или больше лимита. */
    public data object Failed : FileOpenResult
}

/**
 * Системный диалог файлов (US-018, US-024).
 *
 * Реализации: Android — Storage Access Framework, iOS — `UIDocumentPicker`,
 * Desktop — Swing `JFileChooser`. Если платформа не умеет открывать диалог,
 * [isSupported] = `false`, и UI не показывает кнопку.
 */
public interface DocumentGateway {

    public val isSupported: Boolean

    public suspend fun save(fileName: String, content: String, mimeType: String): FileSaveResult

    public suspend fun open(mimeTypes: List<String>): FileOpenResult
}

/** Заглушка для платформ без системного диалога файлов. */
public object UnsupportedDocumentGateway : DocumentGateway {

    override val isSupported: Boolean = false

    override suspend fun save(
        fileName: String,
        content: String,
        mimeType: String,
    ): FileSaveResult = FileSaveResult.Failed

    override suspend fun open(mimeTypes: List<String>): FileOpenResult = FileOpenResult.Failed
}
