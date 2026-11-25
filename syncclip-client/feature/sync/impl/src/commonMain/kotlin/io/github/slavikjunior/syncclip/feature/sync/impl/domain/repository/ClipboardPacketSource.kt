package io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository

import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncPacket
import kotlinx.coroutines.flow.Flow

internal interface ClipboardPacketSource {
    fun outgoingPackets(): Flow<SyncPacket>
}
