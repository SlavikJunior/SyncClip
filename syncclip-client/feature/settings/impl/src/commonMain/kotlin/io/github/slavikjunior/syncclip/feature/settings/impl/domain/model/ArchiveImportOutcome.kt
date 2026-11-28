package io.github.slavikjunior.syncclip.feature.settings.impl.domain.model

/** Итог импорта истории (US-024). */
internal sealed interface ArchiveImportOutcome {

    data class Imported(val imported: Int, val skipped: Int) : ArchiveImportOutcome

    data object Cancelled : ArchiveImportOutcome

    /** Файл не соответствует формату: нет `entries`, битый CSV, чужая версия. */
    data object InvalidFormat : ArchiveImportOutcome

    data object Empty : ArchiveImportOutcome

    data object Failed : ArchiveImportOutcome
}
