package io.github.slavikjunior.syncclip.server.feature.sync.domain

import io.github.slavikjunior.syncclip.server.contracts.port.UserSessionGateway
import io.github.slavikjunior.syncclip.server.contracts.ws.RevocationReason
import io.github.slavikjunior.syncclip.server.contracts.ws.ServerWsMessage
import io.github.slavikjunior.syncclip.server.core.config.LimitsConfig
import io.github.slavikjunior.syncclip.server.core.config.WebSocketConfig
import io.github.slavikjunior.syncclip.server.core.security.SecureTokens
import io.github.slavikjunior.syncclip.server.core.security.TokenBucket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Кадр, который writer-корутина сессии отправит в сокет. */
sealed interface OutboundFrame {
    data class Text(val text: String, val bytes: Int) : OutboundFrame
    data class Close(val code: Short, val reason: String) : OutboundFrame
}

/**
 * Одна WS-сессия. Set сессий на устройство допускает гонку реконнекта (§8.2),
 * `inflightBytes` ограничивает неподтверждённый объём исходящих данных (§8.3).
 */
class DeviceSession(
    val connectionId: String,
    val userId: String,
    val deviceId: String,
    val openedAt: Instant,
    val outbound: Channel<OutboundFrame>,
    val inflightBytes: AtomicLong = AtomicLong(),
    val syncEvents: TokenBucket,
    val acks: TokenBucket,
    val pings: TokenBucket,
    val closeAction: suspend (String) -> Unit,
    val scope: CoroutineScope,
) {
    val closing = AtomicBoolean(false)
}

/**
 * Реестр сессий: реализация порта [UserSessionGateway] (дизайн §8.2).
 * Все отправки — через `trySend`; блокирующих `send` из чужих корутин нет.
 *
 * Rate-бакеты живут на устройство, а не на соединение: реконнект больше не
 * обнуляет счётчик (SEC-08). Число соединений ограничено (PERF-01).
 */
class SessionRegistry(
    private val wsConfig: WebSocketConfig,
    private val limits: LimitsConfig,
    private val json: Json,
    private val tokens: SecureTokens = SecureTokens(),
    private val revocationText: (RevocationReason) -> String = { reason -> reason.defaultMessage() },
    private val metrics: io.github.slavikjunior.syncclip.server.contracts.metrics.MetricsSink =
        io.github.slavikjunior.syncclip.server.contracts.metrics.NoopMetricsSink,
) : UserSessionGateway {

    private val sessions = ConcurrentHashMap<String, DeviceSession>()
    private val byDevice = ConcurrentHashMap<SessionKey, MutableSet<DeviceSession>>()
    private val deviceLimiters = ConcurrentHashMap<SessionKey, DeviceLimiters>()
    private val opensSinceSweep = AtomicLong()

    private data class SessionKey(val userId: String, val deviceId: String)

    fun open(
        userId: String,
        deviceId: String,
        closeAction: suspend (String) -> Unit,
        scope: CoroutineScope,
    ): DeviceSession? {
        val key = SessionKey(userId, deviceId)
        val deviceSessions = byDevice[key]
        if ((deviceSessions?.size ?: 0) >= wsConfig.maxConnectionsPerDevice) return null
        val userConnections = sessions.values.count { it.userId == userId }
        if (userConnections >= wsConfig.maxConnectionsPerUser) return null

        sweepLimitersIfNeeded()
        val buckets = deviceLimiters.computeIfAbsent(key) {
            DeviceLimiters(
                syncEvents = bucket(limits.syncEventsPerMinute),
                acks = bucket(limits.acksPerMinute),
                pings = bucket(limits.pingPerMinute),
            )
        }
        buckets.lastUsedAt.set(System.nanoTime())

        val session = DeviceSession(
            connectionId = tokens.newConnectionId(),
            userId = userId,
            deviceId = deviceId,
            openedAt = Instant.now(),
            outbound = Channel(capacity = wsConfig.outboundCapacity),
            syncEvents = buckets.syncEvents,
            acks = buckets.acks,
            pings = buckets.pings,
            closeAction = closeAction,
            scope = scope,
        )
        sessions[session.connectionId] = session
        byDevice.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }.add(session)
        metrics.increment("syncclip_ws_connections_total")
        return session
    }

    fun close(session: DeviceSession) {
        sessions.remove(session.connectionId)
        val key = SessionKey(session.userId, session.deviceId)
        byDevice[key]?.let { set ->
            set.remove(session)
            if (set.isEmpty()) byDevice.remove(key, set)
        }
        metrics.increment("syncclip_ws_disconnections_total")
    }

    fun activeConnections(): Int = sessions.size

    override fun isOnline(userId: String, deviceId: String): Boolean =
        byDevice[SessionKey(userId, deviceId)]?.isNotEmpty() == true

    override suspend fun deliver(userId: String, deviceId: String, message: ServerWsMessage): Boolean {
        val targets = byDevice[SessionKey(userId, deviceId)]?.toList().orEmpty()
        if (targets.isEmpty()) return false
        val frame = textFrame(message)
        var accepted = false
        for (session in targets) {
            if (offerTracked(session, frame)) accepted = true
        }
        return accepted
    }

    override suspend fun broadcast(userId: String, message: ServerWsMessage, exceptDeviceId: String?) {
        val frame = textFrame(message)
        sessions.values
            .asSequence()
            .filter { it.userId == userId && it.deviceId != exceptDeviceId }
            .forEach { offerTracked(it, frame) }
    }

    override suspend fun revokeDevice(userId: String, deviceId: String, reason: RevocationReason) {
        val targets = byDevice[SessionKey(userId, deviceId)]?.toList().orEmpty()
        targets.forEach { revoke(it, reason) }
    }

    override suspend fun revokeAllOfUser(userId: String, reason: RevocationReason) {
        sessions.values.filter { it.userId == userId }.forEach { revoke(it, reason) }
    }

    /** Graceful shutdown: close 1001 всем сессиям (§13.5). */
    suspend fun shutdownAll() {
        sessions.values.toList().forEach { session ->
            offerControl(session, OutboundFrame.Close(1001, "going away"))
            session.closeAction("going away")
        }
    }

    suspend fun send(session: DeviceSession, message: ServerWsMessage): Boolean =
        offerTracked(session, textFrame(message))

    /**
     * Учитывает байтовый бюджет и закрывает медленную сессию при переполнении.
     * Стартовые pending-кадры идут этим же путём, поэтому writer всегда
     * декрементирует только учтённые байты (PERF-01/PERF-07).
     */
    fun offerTracked(session: DeviceSession, frame: OutboundFrame.Text): Boolean {
        if (session.closing.get()) return false
        val projected = session.inflightBytes.addAndGet(frame.bytes.toLong())
        if (projected > wsConfig.maxInflightBytes) {
            session.inflightBytes.addAndGet(-frame.bytes.toLong())
            closeSlow(session)
            return false
        }
        if (session.outbound.trySend(frame).isSuccess) return true
        // Writer не успел забрать кадр — возвращаем бюджет.
        session.inflightBytes.addAndGet(-frame.bytes.toLong())
        closeSlow(session)
        return false
    }

    /**
     * Вариант с ожиданием места в канале для стартовой pending-выдачи:
     * медленный клиент ограничивается бюджетом и таймаутом, а не ложным
     * «slow_consumer» из-за мгновенно заполненного канала (PERF-01).
     */
    suspend fun offerTrackedAwait(
        session: DeviceSession,
        frame: OutboundFrame.Text,
        timeoutMillis: Long = 5_000,
    ): Boolean {
        if (session.closing.get()) return false
        val projected = session.inflightBytes.addAndGet(frame.bytes.toLong())
        if (projected > wsConfig.maxInflightBytes) {
            session.inflightBytes.addAndGet(-frame.bytes.toLong())
            closeSlow(session)
            return false
        }
        val sent = kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) {
            session.outbound.send(frame)
            true
        } ?: false
        if (!sent) {
            session.inflightBytes.addAndGet(-frame.bytes.toLong())
            closeSlow(session)
        }
        return sent
    }

    /** Периодическая очистка бакетов устройств без соединений. */
    fun sweepLimiters() {
        val ttlNanos = LIMITER_TTL.toNanos()
        val now = System.nanoTime()
        deviceLimiters.entries.removeIf { (key, buckets) ->
            !byDevice.containsKey(key) && now - buckets.lastUsedAt.get() > ttlNanos
        }
    }

    private fun bucket(perMinute: Int): TokenBucket =
        if (!limits.enabled) {
            TokenBucket(capacity = Int.MAX_VALUE, refillPerSecond = 0.0)
        } else {
            TokenBucket(perMinute, perMinute / 60.0)
        }

    private fun sweepLimitersIfNeeded() {
        if (opensSinceSweep.incrementAndGet() % LIMITER_SWEEP_EVERY != 0L) return
        sweepLimiters()
    }

    private fun revoke(session: DeviceSession, reason: RevocationReason) {
        offerTracked(session, textFrame(ServerWsMessage.SessionRevoked(reason, revocationText(reason))))
        offerControl(session, OutboundFrame.Close(1000, reason.name.lowercase()))
    }

    private fun textFrame(message: ServerWsMessage): OutboundFrame.Text {
        val text = json.encodeToString(ServerWsMessage.serializer(), message)
        return OutboundFrame.Text(text, text.toByteArray(Charsets.UTF_8).size)
    }

    private fun offerControl(session: DeviceSession, frame: OutboundFrame) {
        if (!session.outbound.trySend(frame).isSuccess) {
            session.scope.launch { session.closeAction((frame as OutboundFrame.Close).reason) }
        }
    }

    private fun closeSlow(session: DeviceSession) {
        if (!session.closing.compareAndSet(false, true)) return
        metrics.increment("syncclip_ws_slow_consumer_total")
        val closeFrame = OutboundFrame.Close(1008, "slow_consumer")
        if (!session.outbound.trySend(closeFrame).isSuccess) {
            session.scope.launch { session.closeAction("slow_consumer") }
        }
    }

    private data class DeviceLimiters(
        val syncEvents: TokenBucket,
        val acks: TokenBucket,
        val pings: TokenBucket,
        val lastUsedAt: AtomicLong = AtomicLong(System.nanoTime()),
    )

    private companion object {
        val LIMITER_TTL: java.time.Duration = java.time.Duration.ofMinutes(10)
        const val LIMITER_SWEEP_EVERY = 256L
    }
}

/** Тексты SESSION_REVOKED; локализация — на стороне app через MessageCatalog. */
internal fun RevocationReason.defaultMessage(): String = when (this) {
    RevocationReason.GUEST_SESSION_EXPIRED -> "Guest session expired"
    RevocationReason.DEVICE_REMOVED -> "Device removed from account"
    RevocationReason.ACCOUNT_DELETED -> "Account deleted"
    RevocationReason.TOKEN_INVALIDATED -> "Session invalidated"
}
