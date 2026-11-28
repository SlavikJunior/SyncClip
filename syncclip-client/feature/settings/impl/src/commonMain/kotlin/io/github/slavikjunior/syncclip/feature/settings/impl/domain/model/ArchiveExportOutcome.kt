package io.github.slavikjunior.syncclip.feature.settings.impl.domain.model

/** Итог экспорта истории (US-018). */
internal sealed interface ArchiveExportOutcome {

    /** [location] — человекочитаемое место файла, если платформа его сообщает. */
    data class Saved(val location: String?) : ArchiveExportOutcome

    data object Cancelled : ArchiveExportOutcome

    data object Failed : ArchiveExportOutcome
}
