package io.github.slavikjunior.syncclip.server.app.wiring

import io.github.slavikjunior.syncclip.server.app.health.healthRoutes
import io.github.slavikjunior.syncclip.server.app.metrics.MetricsRegistry
import io.github.slavikjunior.syncclip.server.app.metrics.metricsRoutes
import io.github.slavikjunior.syncclip.server.contracts.metrics.MetricsSink
import io.github.slavikjunior.syncclip.server.core.config.AppConfig
import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.github.slavikjunior.syncclip.server.core.auth.DeviceAuthenticator
import io.github.slavikjunior.syncclip.server.core.security.ApiRateLimiter
import io.github.slavikjunior.syncclip.server.core.security.TrustedProxyResolver
import io.github.slavikjunior.syncclip.server.feature.auth.api.authRoutes
import io.github.slavikjunior.syncclip.server.feature.auth.domain.AuthService
import io.github.slavikjunior.syncclip.server.feature.auth.domain.GuestService
import io.github.slavikjunior.syncclip.server.feature.auth.domain.MasterPasswordService
import io.github.slavikjunior.syncclip.server.feature.auth.domain.SessionService
import io.github.slavikjunior.syncclip.server.feature.devices.api.deviceRoutes
import io.github.slavikjunior.syncclip.server.feature.devices.domain.DeviceService
import io.github.slavikjunior.syncclip.server.feature.notifications.api.notificationRoutes
import io.github.slavikjunior.syncclip.server.feature.notifications.domain.FcmRegistrationService
import io.github.slavikjunior.syncclip.server.feature.notifications.domain.UnreadFeedService
import io.github.slavikjunior.syncclip.server.feature.sync.api.syncRoutes
import io.github.slavikjunior.syncclip.server.feature.sync.api.syncWebSocket
import io.github.slavikjunior.syncclip.server.feature.sync.domain.SessionRegistry
import io.github.slavikjunior.syncclip.server.feature.sync.domain.SyncService
import io.github.slavikjunior.syncclip.server.feature.sync.domain.OfflineQueueService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.http.HttpHeaders
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.get

/**
 * Сборка роутов из сервисов (R-5): фичи не знают друг о друге, `app` связывает их.
 */
fun Application.configureRouting() {
    val config = get<AppConfig>()
    val databaseFactory = get<DatabaseFactory>()
    val json = get<Json>()
    val rateLimiter = get<ApiRateLimiter>()
    val deviceAuthenticator = get<DeviceAuthenticator>()
    val clientIpResolver = TrustedProxyResolver(config.http.trustedProxies)
    // X-Forwarded-For читается только от доверенного сокетного адреса (SEC-05).
    val clientIp: (ApplicationCall) -> String = { call ->
        clientIpResolver.resolve(
            remoteAddress = call.request.local.remoteAddress,
            forwardedFor = call.request.headers[HttpHeaders.XForwardedFor],
        )
    }

    val authService = get<AuthService>()
    val sessionService = get<SessionService>()
    val masterPasswordService = get<MasterPasswordService>()
    val guestService = get<GuestService>()
    val deviceService = get<DeviceService>()
    val syncService = get<SyncService>()
    val offlineQueueService = get<OfflineQueueService>()
    val sessionRegistry = get<SessionRegistry>()
    val metrics = get<MetricsRegistry>()
    val metricsSink: MetricsSink = get()
    val fcmRegistrationService = get<FcmRegistrationService>()
    val unreadFeedService = get<UnreadFeedService>()

    routing {
        healthRoutes(databaseFactory)
        if (config.observability.metricsEnabled) {
            metricsRoutes(
                metrics = metrics,
                sessions = sessionRegistry,
                queue = offlineQueueService,
                token = requireNotNull(config.observability.metricsToken) {
                    "METRICS_TOKEN is required when METRICS_ENABLED=true"
                },
            )
        }

        authRoutes(
            authService = authService,
            sessionService = sessionService,
            masterPasswordService = masterPasswordService,
            guestService = guestService,
            deviceAuthenticator = deviceAuthenticator,
            rateLimiter = rateLimiter,
            clientIp = clientIp,
            authConfig = config.auth,
            firebaseVerifier = get(),
        )
        deviceRoutes(
            deviceService = deviceService,
            deviceAuthenticator = deviceAuthenticator,
            rateLimiter = rateLimiter,
        )
        syncRoutes(
            syncService = syncService,
            deviceAuthenticator = deviceAuthenticator,
            rateLimiter = rateLimiter,
        )
        syncWebSocket(
            syncService = syncService,
            registry = sessionRegistry,
            deviceAuthenticator = deviceAuthenticator,
            json = json,
            wsConfig = config.ws,
            metrics = metricsSink,
        )
        notificationRoutes(
            fcmRegistrationService = fcmRegistrationService,
            unreadFeedService = unreadFeedService,
            deviceAuthenticator = deviceAuthenticator,
            rateLimiter = rateLimiter,
        )
    }
}
