package io.github.slavikjunior.syncclip.server.contracts.dto.sync

import kotlinx.serialization.Serializable

/** `totalCount` — размер очереди после фильтра lastEventId (CG-12/CG-23). */
@Serializable
data class PendingResponse(
    val packets: List<SyncPacketDto>,
    val totalCount: Int,
)
