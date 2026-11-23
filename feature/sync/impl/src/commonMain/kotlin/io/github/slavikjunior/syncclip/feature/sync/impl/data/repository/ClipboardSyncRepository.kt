package io.github.slavikjunior.syncclip.feature.sync.impl.data.repository

import io.github.slavikjunior.syncclip.feature.sync.impl.data.source.ClipboardSource
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncPacket
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardPacketSource
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.DebounceClipboardUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class ClipboardSyncRepository(
    private val source: ClipboardSource,
    private val debounce: DebounceClipboardUseCase,
) : ClipboardPacketSource {

    override fun outgoingPackets(): Flow<SyncPacket> = debounce(
        source.observe().map { payload ->
            SyncPacket(
                text = payload.text,
                copiedAtMillis = payload.copiedAtMillis,
                isProgrammatic = payload.isProgrammatic,
            )
        },
    )
}
