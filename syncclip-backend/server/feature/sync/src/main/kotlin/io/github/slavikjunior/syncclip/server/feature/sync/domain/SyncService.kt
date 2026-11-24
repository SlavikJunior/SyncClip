package io.github.slavikjunior.syncclip.server.feature.sync.domain

import io.github.slavikjunior.syncclip.server.contracts.dto.sync.PendingResponse
import io.github.slavikjunior.syncclip.server.contracts.dto.sync.SyncPacketDto
import io.github.slavikjunior.syncclip.server.contracts.error.ErrorCode
import io.github.slavikjunior.syncclip.server.contracts.error.ForbiddenException
import io.github.slavikjunior.syncclip.server.contracts.error.ValidationException
import io.github.slavikjunior.syncclip.server.contracts.model.AuthContext
import io.github.slavikjunior.syncclip.server.contracts.port.UserSessionGateway
import io.github.slavikjunior.syncclip.server.contracts.validation.Validators
import io.github.slavikjunior.syncclip.server.contracts.ws.ClientWsMessage
import io.github.slavikjunior.syncclip.server.contracts.ws.ServerWsMessage
import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.filterOwnedDeviceIds
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.listDevicesByUser
import io.github.slavikjunior.syncclip.server.core.database.tx
import java.time.Instant
import java.util.Base64

/**
 * Relay и pending. Порядок §8.4: валидация → persist (очередь) → online-доставка.
 * Валидация бросает [ValidationException]/[io.github.slavikjunior.syncclip.server.contracts.error.PayloadTooLargeException];
 * WS-слой конвертирует их в `ERROR`.
 */
class SyncService(
    private val databaseFactory: DatabaseFactory,
    private val queue: OfflineQueueService,
    private val sessionGateway: UserSessionGateway,
    private val clock: () -> Instant = Instant::now,
    private val metrics: io.github.slavikjunior.syncclip.server.contracts.metrics.MetricsSink =
        io.github.slavikjunior.syncclip.server.contracts.metrics.NoopMetricsSink,
) {

    suspend fun pending(context: AuthContext, deviceId: String, lastEventId: String?): PendingResponse {
        val normalized = Validators.uuid(deviceId, "deviceId")
        if (normalized != context.deviceId) throw ForbiddenException(ErrorCode.DEVICE_MISMATCH)
        val view = queue.pending(context.userId, normalized, lastEventId)
        return PendingResponse(
            packets = view.packets.map { packet ->
                SyncPacketDto(
                    eventId = packet.eventId,
                    originDeviceId = packet.originDeviceId,
                    encryptedPayload = Base64.getEncoder().encodeToString(packet.encryptedPayload),
                    payloadHash = packet.payloadHash,
                    timestamp = packet.clientTimestamp,
                )
            },
            totalCount = view.totalCount,
        )
    }

    /** Очередь как страницы server→client сообщений (первый кадр после 101, §8.6). */
    suspend fun pendingPage(userId: String, deviceId: String, afterSeq: Long?, limit: Int = PENDING_PAGE_SIZE): PendingPage {
        val packets = queue.pendingPage(userId, deviceId, afterSeq, limit)
        return PendingPage(
            messages = packets.map { packet ->
                ServerWsMessage.SyncEvent(
                    eventId = packet.eventId,
                    originDeviceId = packet.originDeviceId,
                    encryptedPayload = Base64.getEncoder().encodeToString(packet.encryptedPayload),
                    payloadHash = packet.payloadHash,
                    timestamp = packet.clientTimestamp,
                )
            },
            lastSeq = packets.lastOrNull()?.seq,
            hasMore = packets.size == limit,
        )
    }

    suspend fun relay(context: AuthContext, event: ClientWsMessage.SyncEvent): RelayReport {
        val startedAt = System.nanoTime()
        // Анти-спуфинг (T-6): origin обязан совпадать с устройством сессии.
        if (event.originDeviceId != context.deviceId) {
            throw ValidationException("originDeviceId", "spoofed")
        }
        // Невалидный eventId должен давать ERROR, а не закрывать WS (FUNC-04),
        // поэтому проверяем его до любых ранних return'ов.
        val eventId = Validators.uuid(event.eventId, "eventId")
        val payload = Validators.encryptedPayload(event.encryptedPayload)
        val hash = Validators.payloadHash(event.payloadHash)
        val timestamp = Validators.eventTimestamp(event.timestamp, clock().toEpochMilli())
        val requested = Validators.targetDeviceIds(event.targetDeviceIds, context.deviceId)

        val targets = resolveTargets(context, requested)
        if (targets.isEmpty()) return RelayReport(0, 0, 0, 0, 0)

        val packet = QueuedPacket(
            userId = context.userId,
            eventId = eventId,
            originDeviceId = context.deviceId,
            encryptedPayload = payload,
            payloadHash = hash,
            clientTimestamp = timestamp,
        )
        // persist-then-send: запись до попытки online-доставки (§9.1).
        val report = queue.enqueue(packet, targets)

        val outbound = ServerWsMessage.SyncEvent(
            eventId = eventId,
            originDeviceId = context.deviceId,
            encryptedPayload = Base64.getEncoder().encodeToString(payload),
            payloadHash = hash,
            timestamp = timestamp,
        )
        var delivered = 0
        for (target in targets) {
            if (sessionGateway.deliver(context.userId, target, outbound)) delivered++
        }
        val latency = (System.nanoTime() - startedAt) / 1_000_000
        metrics.increment("syncclip_relay_events_total")
        metrics.observe("syncclip_relay_latency_seconds", latency / 1000.0)
        metrics.addCounter("syncclip_relay_targets_total", report.targets.toLong())
        metrics.addCounter("syncclip_relay_online_deliveries_total", delivered.toLong())
        metrics.addCounter("syncclip_relay_duplicates_total", report.duplicates.toLong())
        metrics.addCounter("syncclip_relay_evicted_total", report.evicted.toLong())
        return RelayReport(
            targets = report.targets,
            inserted = report.inserted,
            duplicates = report.duplicates,
            evicted = report.evicted,
            deliveredOnline = delivered,
            latencyMillis = latency,
            sentAt = clock(),
        )
    }

    suspend fun ack(context: AuthContext, ack: ClientWsMessage.Ack): Boolean {
        if (ack.deviceId != context.deviceId) throw ValidationException("deviceId", "device_mismatch")
        return queue.ack(context.userId, context.deviceId, ack.eventId)
    }

    /**
     * Пустой список target'ов = всем устройствам аккаунта кроме отправителя (CG-09).
     * Чужой/неизвестный target — INVALID_PACKET без раскрытия существования.
     */
    private suspend fun resolveTargets(context: AuthContext, requested: List<String>): List<String> {
        if (requested.isEmpty()) {
            val ownDevices = tx(databaseFactory.database) { listDevicesByUser(context.userId) }
            return ownDevices.map { it.id }.filterNot { it == context.deviceId }
        }
        // Явные target'ы проверяются точечным запросом, без чтения всех устройств (PERF-06).
        val owned = tx(databaseFactory.database) { filterOwnedDeviceIds(context.userId, requested) }
        if (requested.any { it !in owned }) throw ValidationException("targetDeviceIds", "unknown_target")
        return requested.filterNot { it == context.deviceId }
    }

    private companion object {
        const val PENDING_PAGE_SIZE = 5
    }
}
