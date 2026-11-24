package io.github.slavikjunior.syncclip.server.app.metrics

import io.github.slavikjunior.syncclip.server.feature.sync.domain.OfflineQueueService
import io.github.slavikjunior.syncclip.server.feature.sync.domain.SessionRegistry
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.security.MessageDigest

/**
 * `GET /metrics` в формате Prometheus (OPS-07). Включается `METRICS_ENABLED=true`
 * и защищён `METRICS_TOKEN` (Bearer или `X-Metrics-Token`), чтобы случайно не
 * выставить телеметрию наружу.
 */
fun Route.metricsRoutes(
    metrics: MetricsRegistry,
    sessions: SessionRegistry,
    queue: OfflineQueueService,
    token: String,
) {
    get("/metrics") {
        if (!matchesToken(call.request.headers["X-Metrics-Token"], token) &&
            !matchesToken(call.request.headers["Authorization"]?.removePrefix("Bearer ")?.trim(), token)
        ) {
            call.respondText("unauthorized\n", ContentType.Text.Plain, HttpStatusCode.Unauthorized)
            return@get
        }
        val body = buildString {
            append(metrics.render())
            appendLine("# TYPE syncclip_ws_active_connections gauge")
            appendLine("syncclip_ws_active_connections ${sessions.activeConnections()}")
            appendLine("# TYPE syncclip_offline_queue_depth gauge")
            appendLine("syncclip_offline_queue_depth ${queue.depth()}")
        }
        call.respondText(body, ContentType.Text.Plain)
    }
}

private fun matchesToken(provided: String?, expected: String): Boolean {
    if (provided.isNullOrBlank()) return false
    return MessageDigest.isEqual(provided.toByteArray(), expected.toByteArray())
}
