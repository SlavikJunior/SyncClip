package io.github.slavikjunior.syncclip.server.feature.devices.api

import io.github.slavikjunior.syncclip.server.contracts.ApiPaths
import io.github.slavikjunior.syncclip.server.contracts.AuthProviderNames
import io.github.slavikjunior.syncclip.server.contracts.dto.device.DeviceEnvelope
import io.github.slavikjunior.syncclip.server.contracts.dto.device.DeviceListDto
import io.github.slavikjunior.syncclip.server.contracts.dto.device.UpdateDeviceRequest
import io.github.slavikjunior.syncclip.server.contracts.error.ErrorCode
import io.github.slavikjunior.syncclip.server.contracts.error.NotFoundException
import io.github.slavikjunior.syncclip.server.contracts.validation.Validators
import io.github.slavikjunior.syncclip.server.core.auth.DeviceAuthenticator
import io.github.slavikjunior.syncclip.server.core.security.ApiRateLimiter
import io.github.slavikjunior.syncclip.server.core.security.JwtClaims
import io.github.slavikjunior.syncclip.server.core.security.RateLimitScope
import io.github.slavikjunior.syncclip.server.feature.devices.domain.DeviceService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch

fun Route.deviceRoutes(
    deviceService: DeviceService,
    deviceAuthenticator: DeviceAuthenticator,
    rateLimiter: ApiRateLimiter,
) {
    authenticate(AuthProviderNames.JWT) {
        get(ApiPaths.DEVICES) {
            val context = call.requireContext(deviceAuthenticator)
            rateLimiter.check(RateLimitScope.DEFAULT, context.userId)
            call.respond(DeviceListDto(deviceService.list(context)))
        }

        patch(ApiPaths.DEVICE_BY_ID) {
            val context = call.requireContext(deviceAuthenticator)
            rateLimiter.check(RateLimitScope.DEFAULT, context.userId)
            val deviceId = Validators.uuid(
                call.parameters["deviceId"] ?: throw NotFoundException(ErrorCode.DEVICE_NOT_FOUND),
                "deviceId",
            )
            val request = call.receive<UpdateDeviceRequest>()
            val device = deviceService.update(context, deviceId, request.name, request.icon)
            call.respond(DeviceEnvelope(device))
        }

        delete(ApiPaths.DEVICE_BY_ID) {
            val context = call.requireContext(deviceAuthenticator)
            rateLimiter.check(RateLimitScope.DEFAULT, context.userId)
            val deviceId = Validators.uuid(
                call.parameters["deviceId"] ?: throw NotFoundException(ErrorCode.DEVICE_NOT_FOUND),
                "deviceId",
            )
            deviceService.delete(context, deviceId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private suspend fun ApplicationCall.requireContext(authenticator: DeviceAuthenticator) =
    claims().let { claims ->
        authenticator.authenticate(
            userId = claims.subject,
            deviceId = claims.deviceId,
            tokenVersion = claims.tokenVersion,
        )
    }

private fun ApplicationCall.claims(): JwtClaims =
    JwtClaims.fromPayload(
        principal<JWTPrincipal>()?.payload
            ?: throw io.github.slavikjunior.syncclip.server.contracts.error.UnauthorizedException(),
    )
