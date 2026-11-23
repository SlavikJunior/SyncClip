package io.github.slavikjunior.syncclip.feature.history.impl.presentation.state

import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntry

/** Запись истории для тестов; значения по умолчанию не важны. */
internal fun historyEntry(id: String, text: String = "text-$id"): HistoryEntry = HistoryEntry(
    id = id,
    text = text,
    timestampMillis = 1_000L,
    sourceDeviceId = null,
    isPinned = false,
)
