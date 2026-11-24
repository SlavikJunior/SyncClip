package io.github.slavikjunior.syncclip.server.feature.auth.api

import io.github.slavikjunior.syncclip.server.contracts.ApiPaths
import io.github.slavikjunior.syncclip.server.contracts.AuthProviderNames
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.AuthResponse
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.CanaryAttemptRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.CanaryAttemptResponse
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.CanaryDto
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.FirebaseLoginRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.GuestExchangeRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.GuestExchangeResponse
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.GuestInviteResponse
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.LoginRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.LogoutRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.RefreshRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.RegisterRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.SuccessDto
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.TokenPairDto
import io.github.slavikjunior.syncclip.server.contracts.error.ErrorCode
import io.github.slavikjunior.syncclip.server.contracts.error.ForbiddenException
import io.github.slavikjunior.syncclip.server.contracts.error.UnauthorizedException
import io.github.slavikjunior.syncclip.server.contracts.error.ValidationException
import io.github.slavikjunior.syncclip.server.contracts.validation.Validators
import io.github.slavikjunior.syncclip.server.core.auth.DeviceAuthenticator
import io.github.slavikjunior.syncclip.server.core.config.AuthConfig
import io.github.slavikjunior.syncclip.server.core.security.ApiRateLimiter
import io.github.slavikjunior.syncclip.server.core.security.RateLimitScope
import io.github.slavikjunior.syncclip.server.feature.auth.domain.AuthService
import io.github.slavikjunior.syncclip.server.feature.auth.domain.FirebaseVerifier
import io.github.slavikjunior.syncclip.server.feature.auth.domain.GuestService
import io.github.slavikjunior.syncclip.server.feature.auth.domain.MasterPasswordService
import io.github.slavikjunior.syncclip.server.feature.auth.domain.RefreshOutcome
import io.github.slavikjunior.syncclip.server.feature.auth.domain.SessionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * Слой API: разбор DTO, лимиты, вызов сервиса, ответ. Никакого SQL и бизнес-правил.
 */
fun Route.authRoutes(
    authService: AuthService,
    sessionService: SessionService,
    masterPasswordService: MasterPasswordService,
    guestService: GuestService,
    deviceAuthenticator: DeviceAuthenticator,
    rateLimiter: ApiRateLimiter,
    clientIp: (ApplicationCall) -> String,
    authConfig: AuthConfig,
    firebaseVerifier: FirebaseVerifier,
) {
    if (authConfig.isFirebase) {
        post(ApiPaths.AUTH_FIREBASE) {
            val ip = clientIp(call)
            rateLimiter.check(RateLimitScope.LOGIN, ip)
            val request = call.receive<FirebaseLoginRequest>()
            if (request.idToken.isBlank()) throw ValidationException("idToken", "empty")
            val identity = firebaseVerifier.verify(request.idToken)
            val session = authService.firebaseLogin(
                identity = identity,
                command = request.toCommand(),
                meta = call.requestMeta(ip),
            )
            call.respond(
                AuthResponse(
                    userId = session.userId,
                    accessToken = session.tokens.accessToken,
                    refreshToken = session.tokens.refreshToken,
                ),
            )
        }
    } else {
        post(ApiPaths.AUTH_REGISTER) {
            val ip = clientIp(call)
            rateLimiter.check(RateLimitScope.REGISTER, ip)
            val command = call.receive<RegisterRequest>().toCommand()
            val session = authService.register(command, call.requestMeta(ip))
            call.respond(
                HttpStatusCode.Created,
                AuthResponse(
                    userId = session.userId,
                    accessToken = session.tokens.accessToken,
                    refreshToken = session.tokens.refreshToken,
                ),
            )
        }

        post(ApiPaths.AUTH_LOGIN) {
            val ip = clientIp(call)
            rateLimiter.check(RateLimitScope.LOGIN, ip)
            val command = call.receive<LoginRequest>().toCommand()
            val session = authService.login(command, call.requestMeta(ip))
            call.respond(
                AuthResponse(
                    userId = session.userId,
                    accessToken = session.tokens.accessToken,
                    refreshToken = session.tokens.refreshToken,
                ),
            )
        }
    }

    post(ApiPaths.AUTH_REFRESH) {
        rateLimiter.check(RateLimitScope.REFRESH, clientIp(call))
        val request = call.receive<RefreshRequest>()
        if (request.refreshToken.isBlank()) throw ValidationException("refreshToken", "empty")
        when (val outcome = sessionService.refresh(request.refreshToken)) {
            is RefreshOutcome.Success -> call.respond(
                TokenPairDto(
                    accessToken = outcome.tokens.accessToken,
                    refreshToken = outcome.tokens.refreshToken,
                ),
            )

            RefreshOutcome.Invalid -> throw UnauthorizedException(ErrorCode.TOKEN_INVALID)
            RefreshOutcome.Expired -> throw UnauthorizedException(ErrorCode.TOKEN_EXPIRED)
            RefreshOutcome.DeviceRevoked -> throw UnauthorizedException(ErrorCode.DEVICE_REVOKED)
            is RefreshOutcome.Reused -> throw UnauthorizedException(ErrorCode.REFRESH_TOKEN_REUSED)
        }
    }

    post(ApiPaths.AUTH_GUEST_EXCHANGE) {
        val ip = clientIp(call)
        rateLimiter.check(RateLimitScope.GUEST_EXCHANGE, ip)
        val command = call.receive<GuestExchangeRequest>().toCommand()
        val session = guestService.exchange(command, call.requestMeta(ip))
        call.respond(
            HttpStatusCode.Created,
            GuestExchangeResponse(
                userId = session.userId,
                deviceId = session.deviceId,
                accessToken = session.accessToken,
                expiresAt = session.expiresAt.toString(),
            ),
        )
    }

    authenticate(AuthProviderNames.JWT) {
        get(ApiPaths.AUTH_CANARY) {
            val context = call.requireContext(deviceAuthenticator)
            rateLimiter.check(RateLimitScope.CANARY, context.userId)
            val canary = masterPasswordService.getCanary(context)
            call.respond(CanaryDto(canary))
        }

        post(ApiPaths.AUTH_CANARY) {
            val context = call.requireContext(deviceAuthenticator)
            rateLimiter.check(RateLimitScope.CANARY, context.userId)
            val request = call.receive<CanaryDto>()
            val encrypted = Validators.encryptedCanary(request.encryptedCanary)
            masterPasswordService.setCanary(context, encrypted)
            call.respond(HttpStatusCode.Created, SuccessDto())
        }

        post(ApiPaths.AUTH_CANARY_ATTEMPT) {
            val context = call.requireContext(deviceAuthenticator)
            rateLimiter.check(RateLimitScope.CANARY_ATTEMPT, context.userId)
            val request = call.receive<CanaryAttemptRequest>()
            val remaining = masterPasswordService.reportAttempt(
                context,
                request.success,
                call.requestMeta(clientIp(call)),
            )
            call.respond(CanaryAttemptResponse(remainingAttempts = remaining))
        }

        post(ApiPaths.AUTH_GUEST_INVITE) {
            val context = call.requireContext(deviceAuthenticator)
            if (context.isGuest) throw ForbiddenException(ErrorCode.FORBIDDEN_FOR_GUEST)
            rateLimiter.check(RateLimitScope.GUEST_INVITE, context.deviceId)
            val invite = guestService.createInvite(context)
            call.respond(
                HttpStatusCode.Created,
                GuestInviteResponse(
                    inviteToken = invite.inviteToken,
                    expiresAt = invite.expiresAt.toString(),
                    shareUri = guestService.shareUri(invite.inviteToken),
                ),
            )
        }

        post(ApiPaths.AUTH_LOGOUT) {
            val context = call.requireContext(deviceAuthenticator)
            rateLimiter.check(RateLimitScope.LOGOUT, context.deviceId)
            val request = call.receive<LogoutRequest>()
            // Гость не управляет сессиями аккаунта (SEC-01).
            if (request.allDevices && context.isGuest) throw ForbiddenException(ErrorCode.FORBIDDEN_FOR_GUEST)
            sessionService.logout(context, request.refreshToken, request.allDevices)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

internal suspend fun ApplicationCall.requireContext(authenticator: DeviceAuthenticator) =
    authenticator.authenticate(
        userId = jwtClaims().subject,
        deviceId = jwtClaims().deviceId,
        tokenVersion = jwtClaims().tokenVersion,
    )
