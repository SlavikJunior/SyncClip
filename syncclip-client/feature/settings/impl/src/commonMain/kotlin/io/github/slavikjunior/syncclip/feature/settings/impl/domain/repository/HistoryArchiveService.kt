package io.github.slavikjunior.syncclip.feature.settings.impl.domain.repository

import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.ArchiveExportOutcome
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.ArchiveFormat
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.ArchiveImportOutcome

/**
 * Файловый обмен историей (US-018, US-024).
 *
 * Контракт в `domain`, реализация — в `data`: домен не знает ни про Room-архив,
 * ни про системный диалог файлов.
 */
internal interface HistoryArchiveService {

    suspend fun export(format: ArchiveFormat): ArchiveExportOutcome

    suspend fun import(): ArchiveImportOutcome
}
