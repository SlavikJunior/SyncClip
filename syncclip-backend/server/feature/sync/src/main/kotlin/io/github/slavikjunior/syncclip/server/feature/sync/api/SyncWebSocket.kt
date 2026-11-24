package io.github.slavikjunior.syncclip.server.feature.sync.api

import io.github.slavikjunior.syncclip.server.contracts.ApiPaths
import io.github.slavikjunior.syncclip.server.contracts.AuthProviderNames
import io.github.slavikjunior.syncclip.server.contracts.error.AppException
import io.github.slavikjunior.syncclip.server.contracts.error.WsErrorCode
import io.github.slavikjunior.syncclip.server.contracts.validation.Validators
import io.github.slavikjunior.syncclip.server.contracts.ws.ClientWsMessage
import io.github.slavikjunior.syncclip.server.contracts.ws.ServerWsMessage
import io.github.slavikjunior.syncclip.server.core.config.WebSocketConfig
import io.github.slavikjunior.syncclip.server.core.auth.DeviceAuthenticator
import io.github.slavikjunior.syncclip.server.feature.sync.domain.DeviceSession
import io.github.slavikjunior.syncclip.server.feature.sync.domain.OutboundFrame
import io.github.slavikjunior.syncclip.server.feature.sync.domain.SessionRegistry
import io.github.slavikjunior.syncclip.server.feature.sync.domain.SyncService
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private const val CLOSE_VIOLATED_POLICY: Short = 1008
private const val CLOSE_INTERNAL_ERROR: Short = 1011
private const val CLOSE_UNAUTHORIZED: Short = 4401
private const val CLOSE_FORBIDDEN: Short = 4403
private const val MAX_CONSECUTIVE_ERRORS = 10

/**
 * Порог нарушений rate limit до закрытия соединения. Клиент не знает точное
 * число оставшихся токенов, поэтому burst из 100+ событий даёт несколько
 * RATE_LIMIT_EXCEEDED подряд; рвать связь после третьего слишком агрессивно.
 */
private const val MAX_RATE_VIOLATIONS = 20

/**
 * WS-хендшейк и цикл чтения (§8.1, §8.4). Порядок: auth (до 101) → сверка
 * deviceId → регистрация сессии → writer → pending первым кадром → read loop.
 */
fun Route.syncWebSocket(
    syncService: SyncService,
    registry: SessionRegistry,
    deviceAuthenticator: DeviceAuthenticator,
    json: Json,
    wsConfig: WebSocketConfig,
    metrics: io.github.slavikjunior.syncclip.server.contracts.metrics.MetricsSink =
        io.github.slavikjunior.syncclip.server.contracts.metrics.NoopMetricsSink,
) {
    val logger = LoggerFactory.getLogger("SyncWebSocket")

    authenticate(AuthProviderNames.JWT) {
        webSocket(ApiPaths.SYNC_WS) {
            val claims = runCatching { call.claims() }.getOrNull()
            if (claims == null) {
                close(CloseReason(CLOSE_UNAUTHORIZED, "token invalid"))
                return@webSocket
            }
            // UUID нормализуется до сравнения: uppercase от клиента — валиден (FUNC-09).
            val queryDeviceId = call.request.queryParameters[ApiPaths.DEVICE_ID_PARAM]
                ?.let { raw -> runCatching { Validators.uuid(raw, "deviceId") }.getOrNull() }
            if (queryDeviceId == null || queryDeviceId != claims.deviceId) {
                close(CloseReason(CLOSE_FORBIDDEN, "device mismatch"))
                return@webSocket
            }
            val context = try {
                deviceAuthenticator.authenticate(claims.subject, claims.deviceId, claims.tokenVersion)
            } catch (e: AppException) {
                close(CloseReason(CLOSE_UNAUTHORIZED, e.code.restCode))
                return@webSocket
            }

            val session = registry.open(
                userId = context.userId,
                deviceId = context.deviceId,
                closeAction = { reason -> close(CloseReason(CLOSE_VIOLATED_POLICY, reason)) },
                scope = this,
            )
            if (session == null) {
                metrics.increment("syncclip_ws_connection_rejected_total")
                logger.warn(
                    "ws connection limit reached userId={} deviceId={}",
                    context.userId,
                    context.deviceId,
                )
                close(CloseReason(CLOSE_VIOLATED_POLICY, "connection limit"))
                return@webSocket
            }
            logger.info(
                "ws open connectionId={} userId={} deviceId={}",
                session.connectionId,
                context.userId,
                context.deviceId,
            )
            val writer = launchWriter(session)

            try {
                sendInitialPending(
                    session,
                    context.userId,
                    context.deviceId,
                    syncService,
                    registry,
                    json,
                    wsConfig.maxInitialPendingBytes,
                )
                readLoop(session, context, syncService, registry, json, logger, metrics)
            } finally {
                registry.close(session)
                writer.cancel()
                logger.info(
                    "ws close connectionId={} deviceId={}",
                    session.connectionId,
                    context.deviceId,
                )
            }
        }
    }
}

/**
 * Pending отдаётся страницами и через `offerTracked`: очередь не материализуется
 * целиком, а стартовые кадры учитываются в `inflightBytes` (PERF-01/PERF-07).
 */
private suspend fun DefaultWebSocketServerSession.sendInitialPending(
    session: DeviceSession,
    userId: String,
    deviceId: String,
    syncService: SyncService,
    registry: SessionRegistry,
    json: Json,
    maxBytes: Long,
) {
    var afterSeq: Long? = null
    var sentBytes = 0L
    while (true) {
        val page = syncService.pendingPage(userId, deviceId, afterSeq)
        var stop = false
        for (message in page.messages) {
            val text = json.encodeToString(ServerWsMessage.serializer(), message)
            val frame = OutboundFrame.Text(text, text.toByteArray(Charsets.UTF_8).size)
            if (sentBytes + frame.bytes > maxBytes) {
                stop = true
                break
            }
            if (!registry.offerTrackedAwait(session, frame)) {
                stop = true
                break
            }
            sentBytes += frame.bytes
        }
        if (stop || !page.hasMore || page.lastSeq == null) return
        afterSeq = page.lastSeq
    }
}

private fun DefaultWebSocketServerSession.launchWriter(session: DeviceSession): Job = launch(Dispatchers.IO) {
    try {
        for (frame in session.outbound) {
            when (frame) {
                is OutboundFrame.Text -> {
                    try {
                        send(Frame.Text(frame.text))
                    } finally {
                        session.inflightBytes.addAndGet(-frame.bytes.toLong())
                    }
                }

                is OutboundFrame.Close -> {
                    runCatching { close(CloseReason(frame.code, frame.reason)) }
                    break
                }
            }
        }
    } catch (e: Exception) {
        // Клиент отключился — это нормальный путь, не ошибка сервера.
    }
}

private suspend fun DefaultWebSocketServerSession.readLoop(
    session: DeviceSession,
    context: io.github.slavikjunior.syncclip.server.contracts.model.AuthContext,
    syncService: SyncService,
    registry: SessionRegistry,
    json: Json,
    logger: org.slf4j.Logger,
    metrics: io.github.slavikjunior.syncclip.server.contracts.metrics.MetricsSink,
) {
    var consecutiveErrors = 0
    var rateViolations = 0

    for (frame in incoming) {
        if (frame !is Frame.Text) continue
        val message = runCatching {
            json.decodeFromString(ClientWsMessage.serializer(), frame.readText())
        }.getOrNull()

        if (message == null) {
            consecutiveErrors++
            metrics.increment("syncclip_ws_invalid_packets_total")
            registry.send(session, wsError(WsErrorCode.INVALID_PACKET, null))
            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                close(CloseReason(CLOSE_VIOLATED_POLICY, "invalid packets"))
                return
            }
            continue
        }
        consecutiveErrors = 0

        when (message) {
            is ClientWsMessage.SyncEvent -> {
                if (!session.syncEvents.tryAcquire()) {
                    rateViolations++
                    metrics.increment("syncclip_ws_rate_limited_total", mapOf("type" to "sync_event"))
                    registry.send(session, wsError(WsErrorCode.RATE_LIMIT_EXCEEDED, message.eventId))
                    if (rateViolations >= MAX_RATE_VIOLATIONS) {
                        close(CloseReason(CLOSE_VIOLATED_POLICY, "rate limit"))
                        return
                    }
                    continue
                }
                rateViolations = 0
                try {
                    val report = syncService.relay(context, message)
                    logger.info(
                        "SYNC_EVENT_RELAYED eventId={} targets={} queued={} delivered={} evicted={} latencyMs={}",
                        message.eventId,
                        report.targets,
                        report.inserted,
                        report.deliveredOnline,
                        report.evicted,
                        report.latencyMillis,
                    )
                } catch (e: AppException) {
                    registry.send(session, wsError(e.code.wsCode ?: WsErrorCode.INVALID_PACKET, message.eventId))
                } catch (e: Exception) {
                    logger.error("relay failed eventId={}", message.eventId, e)
                    close(CloseReason(CLOSE_INTERNAL_ERROR, "internal error"))
                    return
                }
            }

            is ClientWsMessage.Ack -> {
                if (!session.acks.tryAcquire()) {
                    metrics.increment("syncclip_ws_rate_limited_total", mapOf("type" to "ack"))
                    registry.send(session, wsError(WsErrorCode.RATE_LIMIT_EXCEEDED, message.eventId))
                    continue
                }
                try {
                    syncService.ack(context, message)
                } catch (e: AppException) {
                    registry.send(session, wsError(e.code.wsCode ?: WsErrorCode.INVALID_PACKET, message.eventId))
                }
            }

            is ClientWsMessage.Ping -> {
                if (!session.pings.tryAcquire()) {
                    metrics.increment("syncclip_ws_rate_limited_total", mapOf("type" to "ping"))
                    registry.send(session, wsError(WsErrorCode.RATE_LIMIT_EXCEEDED, null))
                    continue
                }
                registry.send(session, ServerWsMessage.Pong(message.timestamp))
            }
        }
    }
}

private fun wsError(code: WsErrorCode, eventId: String?) = ServerWsMessage.Error(
    code = code,
    message = code.name,
    eventId = eventId,
)
