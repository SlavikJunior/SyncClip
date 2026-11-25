package io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase

import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncPacket
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNot

/**
 * US-008: пользовательские копирования дебаунсятся на 100 мс, программные
 * вставки не попадают в исходящий поток вообще.
 */
@OptIn(FlowPreview::class)
internal class DebounceClipboardUseCase {

    operator fun invoke(source: Flow<SyncPacket>): Flow<SyncPacket> =
        source
            .filterNot { it.isProgrammatic }
            .debounce(DEBOUNCE_MS)

    private companion object {
        const val DEBOUNCE_MS = 100L
    }
}
