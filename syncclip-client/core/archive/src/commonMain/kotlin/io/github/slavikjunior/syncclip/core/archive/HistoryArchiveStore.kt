package io.github.slavikjunior.syncclip.core.archive

/**
 * Источник и приёмник записей архива истории.
 *
 * Имя без суффикса `Repository` намеренно: контракт лежит в инфраструктурном
 * модуле, а не в `domain`/`api` фичи, и проверка INV-25 для репозиториев к нему
 * не применяется.
 *
 * Экспорт отдаёт не больше [ARCHIVE_ENTRY_LIMIT] последних записей; импорт
 * пропускает записи, чей `id` уже есть в локальной базе (дубль), и не меняет
 * существующие.
 */
public interface HistoryArchiveStore {

    public suspend fun count(): Int

    public suspend fun export(limit: Int = ARCHIVE_ENTRY_LIMIT): List<HistoryArchiveEntry>

    public suspend fun import(entries: List<HistoryArchiveEntry>): ArchiveImportResult
}
