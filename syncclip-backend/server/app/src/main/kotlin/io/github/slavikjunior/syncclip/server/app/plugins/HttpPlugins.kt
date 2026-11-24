package io.github.slavikjunior.syncclip.server.app.plugins

import io.github.slavikjunior.syncclip.server.contracts.util.UuidV7
import io.github.slavikjunior.syncclip.server.core.config.AppConfig
import io.github.slavikjunior.syncclip.server.core.config.AppEnvironment
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import org.slf4j.event.Level

/** HTTP-обвязка: callId, структурный лог, лимит тела, gzip, no-store, доверенные прокси. */
fun Application.configureHttpPlugins(config: AppConfig) {
    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate { UuidV7.newString() }
        replyToHeader(HttpHeaders.XRequestId)
    }

    install(CallLogging) {
        level = Level.INFO
        filter { call -> !call.request.path().startsWith("/health") }
        format { call ->
            val status = call.response.status()?.value ?: "-"
            // Путь без query: токен из query не попадает в лог (CG-07).
            "callId=${call.callId} ${call.request.httpMethod.value} ${call.request.path()} -> $status"
        }
    }

    install(DefaultHeaders) {
        // Токены в теле ответов: кэширование запрещено (§12.7).
        header(HttpHeaders.CacheControl, "no-store")
        header("X-Content-Type-Options", "nosniff")
        if (config.environment == AppEnvironment.PROD) {
            header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        }
    }

    install(Compression) {
        gzip()
    }

    install(RequestBodyLimit) {
        bodyLimit { config.server.requestBodyLimitBytes }
    }

    // Заголовки прокси НЕ ставим плагином: X-Forwarded-For читается вручную
    // только от доверенного сокетного адреса (SEC-05), см. TrustedProxyResolver.
    if (config.http.trustedProxies.isNotEmpty()) {
        log.info("trusted proxies configured: {}", config.http.trustedProxies.size)
    }
}
