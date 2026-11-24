package io.github.slavikjunior.syncclip.server.feature.sync.api

import io.github.slavikjunior.syncclip.server.contracts.error.UnauthorizedException
import io.github.slavikjunior.syncclip.server.core.auth.DeviceAuthenticator
import io.github.slavikjunior.syncclip.server.core.security.JwtClaims
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal

internal fun ApplicationCall.claims(): JwtClaims =
    JwtClaims.fromPayload(principal<JWTPrincipal>()?.payload ?: throw UnauthorizedException())

internal suspend fun ApplicationCall.authContext(authenticator: DeviceAuthenticator): Pair<JwtClaims, io.github.slavikjunior.syncclip.server.contracts.model.AuthContext> {
    val claims = claims()
    val context = authenticator.authenticate(claims.subject, claims.deviceId, claims.tokenVersion)
    return claims to context
}
