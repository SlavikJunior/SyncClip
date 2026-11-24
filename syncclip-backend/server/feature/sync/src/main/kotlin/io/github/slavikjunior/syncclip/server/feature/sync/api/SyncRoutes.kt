package io.github.slavikjunior.syncclip.server.feature.sync.api

import io.github.slavikjunior.syncclip.server.contracts.ApiPaths
import io.github.slavikjunior.syncclip.server.contracts.AuthProviderNames
import io.github.slavikjunior.syncclip.server.contracts.error.ValidationException
import io.github.slavikjunior.syncclip.server.core.auth.DeviceAuthenticator
import io.github.slavikjunior.syncclip.server.core.security.ApiRateLimiter
import io.github.slavikjunior.syncclip.server.core.security.RateLimitScope
import io.github.slavikjunior.syncclip.server.feature.sync.domain.SyncService
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** `GET /sync/pending`: deviceId обязан совпадать с claim `dev` (CG-22). */
fun Route.syncRoutes(
    syncService: SyncService,
    deviceAuthenticator: DeviceAuthenticator,
    rateLimiter: ApiRateLimiter,
) {
    authenticate(AuthProviderNames.JWT) {
        get(ApiPaths.SYNC_PENDING) {
            val (_, context) = call.authContext(deviceAuthenticator)
            rateLimiter.check(RateLimitScope.SYNC_PENDING, context.deviceId)
            val deviceId = call.request.queryParameters[ApiPaths.DEVICE_ID_PARAM]
                ?: throw ValidationException("deviceId", "required")
            val lastEventId = call.request.queryParameters[ApiPaths.LAST_EVENT_ID_PARAM]
            call.respond(syncService.pending(context, deviceId, lastEventId))
        }
    }
}
