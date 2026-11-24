package io.github.slavikjunior.syncclip.server.feature.sync.domain

import io.github.slavikjunior.syncclip.server.core.database.model.QueuedPacketRecord
import java.time.Instant

/** Пакет, который нужно положить в очередь каждому target'у (после валидации). */
data class QueuedPacket(
    val userId: String,
    val eventId: String,
    val originDeviceId: String,
    val encryptedPayload: ByteArray,
    val payloadHash: String,
    val clientTimestamp: Long,
)

data class EnqueueReport(
    val inserted: Int,
    val duplicates: Int,
    val evicted: Int,
) {
    val targets: Int get() = inserted + duplicates
}

data class PendingView(
    val packets: List<QueuedPacketRecord>,
    val totalCount: Int,
)

/** Страница pending для WS-выдачи: `lastSeq` — продолжение, `hasMore` — есть ещё. */
data class PendingPage(
    val messages: List<io.github.slavikjunior.syncclip.server.contracts.ws.ServerWsMessage.SyncEvent>,
    val lastSeq: Long?,
    val hasMore: Boolean,
)

data class RelayReport(
    val targets: Int,
    val inserted: Int,
    val duplicates: Int,
    val evicted: Int,
    val deliveredOnline: Int,
    val latencyMillis: Long = 0,
    val sentAt: Instant = Instant.now(),
)
