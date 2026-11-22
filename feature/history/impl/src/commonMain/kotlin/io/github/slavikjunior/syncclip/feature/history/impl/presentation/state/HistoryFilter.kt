package io.github.slavikjunior.syncclip.feature.history.impl.presentation.state

import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntryType

/** Локальный фильтр списка истории. */
internal enum class HistoryFilter {
    ALL,
    PINNED,
    LINKS,
    CODE,
    ;

    fun matches(entry: HistoryEntryUi): Boolean = when (this) {
        ALL -> true
        PINNED -> entry.isPinned
        LINKS -> entry.type == HistoryEntryType.LINK
        CODE -> entry.type == HistoryEntryType.CODE
    }
}
