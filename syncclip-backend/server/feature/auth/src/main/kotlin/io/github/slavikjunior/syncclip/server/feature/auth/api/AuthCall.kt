package io.github.slavikjunior.syncclip.server.feature.auth.api

import io.github.slavikjunior.syncclip.server.contracts.error.UnauthorizedException
import io.github.slavikjunior.syncclip.server.core.security.JwtClaims
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal

/** Claims из проверенного principal; ничего не доверяем телу запроса (§7.1). */
internal fun ApplicationCall.jwtClaims(): JwtClaims =
    JwtClaims.fromPayload(principal<JWTPrincipal>()?.payload ?: throw UnauthorizedException())
