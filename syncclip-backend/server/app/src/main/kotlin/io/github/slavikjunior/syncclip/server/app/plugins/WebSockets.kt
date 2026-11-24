package io.github.slavikjunior.syncclip.server.app.plugins

import io.github.slavikjunior.syncclip.server.core.config.AppConfig
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets

/**
 * Транспортный heartbeat и лимиты кадра (§8.5, §12.3): ping 30 с, timeout 15 с,
 * maxFrameSize 1 MiB по умолчанию.
 */
fun Application.configureWebSockets(config: AppConfig) {
    install(WebSockets) {
        pingPeriodMillis = config.ws.pingPeriodSeconds * 1000
        timeoutMillis = config.ws.timeoutSeconds * 1000
        maxFrameSize = config.ws.maxFrameBytes
        masking = false
    }
}
