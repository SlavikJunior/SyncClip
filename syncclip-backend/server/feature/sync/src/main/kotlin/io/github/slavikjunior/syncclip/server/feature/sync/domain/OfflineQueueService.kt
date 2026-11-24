package io.github.slavikjunior.syncclip.server.feature.sync.domain

import io.github.slavikjunior.syncclip.server.contracts.error.ValidationException
import io.github.slavikjunior.syncclip.server.contracts.util.UuidV7
import io.github.slavikjunior.syncclip.server.contracts.validation.Validators
import io.github.slavikjunior.syncclip.server.core.config.AppConfig
import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.github.slavikjunior.syncclip.server.core.database.model.QueuedPacketRecord
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.countPendingPackets
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.countQueuedPackets
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.countQueuedPacketsTotal
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.deleteExpiredQueuedPackets
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.deleteQueuedPacketByAck
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.deleteQueuedPacketsByTarget
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.evictOldestQueuedPackets
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.findAckWatermark
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.findQueueAnchorSeq
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.findQueueAnchorUserId
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.insertQueuedPacketIgnore
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.listPendingPackets
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.lockQueueTarget
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.saveAckWatermark
import io.github.slavikjunior.syncclip.server.core.database.tx
import java.time.Instant

/**
 * Очередь (§9): persist-then-send, лимит 30 на устройство с вытеснением самого
 * старого по `seq`, TTL 48 ч, удаление только по ACK или TTL.
 */
class OfflineQueueService(
    private val databaseFactory: DatabaseFactory,
    private val config: AppConfig,
    private val clock: () -> Instant = Instant::now,
) {

    suspend fun enqueue(packet: QueuedPacket, targets: List<String>): EnqueueReport {
        val now = clock()
        val expiresAt = now.plusSeconds(config.queue.ttlHours * 3600)
        val limit = config.queue.limitPerDevice
        return tx(databaseFactory.database) {
            var inserted = 0
            var duplicates = 0
            var evicted = 0
            // Сортировка обязательна: исключает deadlock при пересекающихся наборах (§9.2).
            for (target in targets.distinct().sorted()) {
                lockQueueTarget(target)
                val stored = insertQueuedPacketIgnore(
                    id = UuidV7.newString(),
                    userId = packet.userId,
                    targetDeviceId = target,
                    eventId = packet.eventId,
                    originDeviceId = packet.originDeviceId,
                    encryptedPayload = packet.encryptedPayload,
                    payloadHash = packet.payloadHash,
                    clientTimestamp = packet.clientTimestamp,
                    createdAt = now,
                    expiresAt = expiresAt,
                )
                if (stored) inserted++ else duplicates++
                val excess = countQueuedPackets(packet.userId, target) - limit
                if (excess > 0) evicted += evictOldestQueuedPackets(packet.userId, target, excess)
            }
            EnqueueReport(inserted = inserted, duplicates = duplicates, evicted = evicted)
        }
    }

    /**
     * `lastEventId` резолвится в seq; чужой eventId (другой аккаунт) — ошибка,
     * неизвестный — вся очередь, ACK-нутый — строго после watermark (FUNC-05).
     */
    suspend fun pending(userId: String, deviceId: String, lastEventId: String?): PendingView =
        tx(databaseFactory.database) {
            val anchor = lastEventId?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
                val eventId = Validators.uuid(raw, "lastEventId")
                // Сначала своя строка: при fan-out она может быть любой из цепочки.
                val ownAnchor = findQueueAnchorSeq(userId, deviceId, eventId)
                val watermark = findAckWatermark(userId, deviceId)
                when {
                    ownAnchor != null -> ownAnchor
                    // Пакет уже ACK-нут: продолжаем строго после его seq (FUNC-05).
                    watermark?.eventId == eventId -> watermark.seq
                    else -> {
                        // Fan-out на другое устройство того же аккаунта — не чужой
                        // eventId: событие могло быть ACK-нуто, очередь с начала.
                        val ownerUserId = findQueueAnchorUserId(eventId)
                        if (ownerUserId != null && ownerUserId != userId) {
                            throw ValidationException("lastEventId", "foreign_event")
                        }
                        null
                    }
                }
            }
            val packets = listPendingPackets(userId, deviceId, anchor, config.queue.limitPerDevice)
            val total = countPendingPackets(userId, deviceId, anchor)
            PendingView(packets = packets, totalCount = total)
        }

    /** Одна страница pending для WS: очередь не материализуется целиком (PERF-01). */
    suspend fun pendingPage(userId: String, deviceId: String, afterSeq: Long?, limit: Int): List<QueuedPacketRecord> =
        tx(databaseFactory.database) { listPendingPackets(userId, deviceId, afterSeq, limit) }

    suspend fun ack(userId: String, deviceId: String, eventId: String): Boolean =
        tx(databaseFactory.database) {
            val normalized = Validators.uuid(eventId, "eventId")
            val seq = findQueueAnchorSeq(userId, deviceId, normalized)
            val deleted = deleteQueuedPacketByAck(userId, deviceId, normalized)
            // Watermark двигаем только после успешного удаления строки.
            if (deleted && seq != null) {
                saveAckWatermark(userId, deviceId, normalized, seq, clock())
            }
            deleted
        }

    suspend fun cleanupExpired(): Int =
        tx(databaseFactory.database) { deleteExpiredQueuedPackets(clock()) }

    suspend fun deleteForDevice(userId: String, deviceId: String): Int =
        tx(databaseFactory.database) { deleteQueuedPacketsByTarget(userId, deviceId) }

    suspend fun depth(): Int = tx(databaseFactory.database) { countQueuedPacketsTotal() }
}
