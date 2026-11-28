package io.github.slavikjunior.syncclip.feature.settings.impl.data.archive

import io.github.slavikjunior.syncclip.core.archive.ARCHIVE_ENTRY_LIMIT
import io.github.slavikjunior.syncclip.core.archive.ArchiveError
import io.github.slavikjunior.syncclip.core.archive.HistoryArchiveCodec
import io.github.slavikjunior.syncclip.core.archive.HistoryArchiveFormat
import io.github.slavikjunior.syncclip.core.archive.HistoryArchiveStore
import io.github.slavikjunior.syncclip.core.platform.DocumentGateway
import io.github.slavikjunior.syncclip.core.platform.FileOpenResult
import io.github.slavikjunior.syncclip.core.platform.FileSaveResult
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.ArchiveExportOutcome
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.ArchiveFormat
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.ArchiveImportOutcome
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.repository.HistoryArchiveService
import kotlin.time.Clock

/**
 * Экспорт/импорт истории через системный диалог файлов.
 *
 * Экспорт ограничен [ARCHIVE_ENTRY_LIMIT] записями (US-018): файл не должен
 * съесть память, а предупреждение о лимите показывает UI. Импорт определяет
 * формат по расширению выбранного файла и пропускает дубли по `id`.
 */
internal class DocumentHistoryArchiveService(
    private val archive: HistoryArchiveStore,
    private val documents: DocumentGateway,
    private val clock: Clock = Clock.System,
) : HistoryArchiveService {

    override suspend fun export(format: ArchiveFormat): ArchiveExportOutcome {
        val entries = runCatching { archive.export(ARCHIVE_ENTRY_LIMIT) }.getOrNull()
            ?: return ArchiveExportOutcome.Failed
        val coreFormat = format.toCore()
        val content = runCatching {
            HistoryArchiveCodec.encode(
                format = coreFormat,
                entries = entries,
                exportedAtIso = clock.now().toString(),
            )
        }.getOrNull() ?: return ArchiveExportOutcome.Failed

        return when (
            val result = documents.save(
                fileName = fileName(format, clock.now().toString()),
                content = content,
                mimeType = coreFormat.mimeType,
            )
        ) {
            is FileSaveResult.Saved -> ArchiveExportOutcome.Saved(result.location)
            FileSaveResult.Cancelled -> ArchiveExportOutcome.Cancelled
            FileSaveResult.Failed -> ArchiveExportOutcome.Failed
        }
    }

    override suspend fun import(): ArchiveImportOutcome {
        val opened = when (val result = documents.open(listOf(JSON_MIME, CSV_MIME))) {
            is FileOpenResult.Opened -> result
            FileOpenResult.Cancelled -> return ArchiveImportOutcome.Cancelled
            FileOpenResult.Failed -> return ArchiveImportOutcome.Failed
        }
        val format = if (opened.name.endsWith(".csv", ignoreCase = true)) {
            HistoryArchiveFormat.CSV
        } else {
            HistoryArchiveFormat.JSON
        }
        val entries = HistoryArchiveCodec.decode(format, opened.content)
            .getOrElse { error ->
                return when ((error as? HistoryArchiveCodec.ArchiveFormatException)?.error) {
                    ArchiveError.EMPTY -> ArchiveImportOutcome.Empty
                    else -> ArchiveImportOutcome.InvalidFormat
                }
            }
        val result = runCatching { archive.import(entries) }.getOrNull()
            ?: return ArchiveImportOutcome.Failed
        return ArchiveImportOutcome.Imported(result.imported, result.skipped)
    }

    /** `syncclip_export_2026-11-28.json` — дата из ISO-строки времени. */
    private fun fileName(format: ArchiveFormat, exportedAtIso: String): String {
        val date = exportedAtIso.substringBefore('T')
        return "syncclip_export_$date.${format.toCore().extension}"
    }

    private fun ArchiveFormat.toCore(): HistoryArchiveFormat = when (this) {
        ArchiveFormat.JSON -> HistoryArchiveFormat.JSON
        ArchiveFormat.CSV -> HistoryArchiveFormat.CSV
    }

    private companion object {
        const val JSON_MIME = "application/json"
        const val CSV_MIME = "text/csv"
    }
}
