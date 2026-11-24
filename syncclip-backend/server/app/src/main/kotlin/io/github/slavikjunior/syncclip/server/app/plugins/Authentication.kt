package io.github.slavikjunior.syncclip.server.app.plugins

import io.github.slavikjunior.syncclip.server.contracts.AuthProviderNames
import io.github.slavikjunior.syncclip.server.contracts.error.ErrorCode
import io.github.slavikjunior.syncclip.server.contracts.error.ErrorResponse
import io.github.slavikjunior.syncclip.server.app.i18n.MessageCatalog
import io.github.slavikjunior.syncclip.server.core.config.AppConfig
import io.github.slavikjunior.syncclip.server.core.security.JwtVerifier
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.request.acceptLanguage
import io.ktor.server.response.respond

/**
 * JWT-провайдер. Заголовок `Authorization` приоритетнее query-токена;
 * query разрешён только при `WS_ALLOW_QUERY_TOKEN=true` и не логируется (CG-07).
 */
fun Application.configureAuthentication(config: AppConfig, verifier: JwtVerifier, catalog: MessageCatalog) {
    install(Authentication) {
        jwt(AuthProviderNames.JWT) {
            realm = config.jwt.issuer
            verifier(verifier.delegate)
            authHeader { call ->
                val header = call.request.headers[HttpHeaders.Authorization]
                if (header != null) {
                    val token = header.removePrefix("Bearer").removePrefix("bearer").trim()
                    if (token.isEmpty()) null else HttpAuthHeader.Single("Bearer", token)
                } else if (config.ws.allowQueryToken) {
                    call.request.queryParameters["token"]?.let { HttpAuthHeader.Single("Bearer", it) }
                } else {
                    null
                }
            }
            validate { credential ->
                val type = credential.payload.getClaim("typ").asString()
                if (type == "access" && credential.payload.subject != null) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(
                        ErrorCode.TOKEN_INVALID.restCode,
                        catalog.message(
                            ErrorCode.TOKEN_INVALID.restCode,
                            catalog.locale(call.request.acceptLanguage()),
                        ),
                    ),
                )
            }
        }
    }
}
