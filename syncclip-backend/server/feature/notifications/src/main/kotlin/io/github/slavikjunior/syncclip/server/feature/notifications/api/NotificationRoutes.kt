package io.github.slavikjunior.syncclip.server.feature.notifications.api

import io.github.slavikjunior.syncclip.server.contracts.ApiPaths
import io.github.slavikjunior.syncclip.server.contracts.AuthProviderNames
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.SuccessDto
import io.github.slavikjunior.syncclip.server.contracts.dto.fcm.FcmRegisterRequest
import io.github.slavikjunior.syncclip.server.contracts.error.UnauthorizedException
import io.github.slavikjunior.syncclip.server.core.auth.DeviceAuthenticator
import io.github.slavikjunior.syncclip.server.core.security.ApiRateLimiter
import io.github.slavikjunior.syncclip.server.core.security.JwtClaims
import io.github.slavikjunior.syncclip.server.core.security.RateLimitScope
import io.github.slavikjunior.syncclip.server.feature.notifications.domain.FcmRegistrationService
import io.github.slavikjunior.syncclip.server.feature.notifications.domain.UnreadFeedService
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.notificationRoutes(
    fcmRegistrationService: FcmRegistrationService,
    unreadFeedService: UnreadFeedService,
    deviceAuthenticator: DeviceAuthenticator,
    rateLimiter: ApiRateLimiter,
) {
    authenticate(AuthProviderNames.JWT) {
        post(ApiPaths.FCM_REGISTER) {
            val claims = call.claims()
            val context = deviceAuthenticator.authenticate(claims.subject, claims.deviceId, claims.tokenVersion)
            rateLimiter.check(RateLimitScope.DEFAULT, context.userId)
            val request = call.receive<FcmRegisterRequest>()
            fcmRegistrationService.register(context, request.deviceId, request.fcmToken)
            call.respond(SuccessDto())
        }

        get(ApiPaths.NOTIFICATIONS_UNREAD) {
            val claims = call.claims()
            val context = deviceAuthenticator.authenticate(claims.subject, claims.deviceId, claims.tokenVersion)
            rateLimiter.check(RateLimitScope.DEFAULT, context.userId)
            val since = call.request.queryParameters[ApiPaths.SINCE_PARAM]
            val limit = call.request.queryParameters[ApiPaths.LIMIT_PARAM]?.toIntOrNull() ?: 50
            call.respond(unreadFeedService.unread(context, since, limit))
        }
    }
}

private fun ApplicationCall.claims(): JwtClaims =
    JwtClaims.fromPayload(principal<JWTPrincipal>()?.payload ?: throw UnauthorizedException())
