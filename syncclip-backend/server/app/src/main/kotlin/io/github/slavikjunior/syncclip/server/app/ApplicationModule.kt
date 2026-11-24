package io.github.slavikjunior.syncclip.server.app

import io.github.slavikjunior.syncclip.server.app.di.serverModule
import io.github.slavikjunior.syncclip.server.app.jobs.MaintenanceScheduler
import io.github.slavikjunior.syncclip.server.app.plugins.configureAuthentication
import io.github.slavikjunior.syncclip.server.app.plugins.configureHttpPlugins
import io.github.slavikjunior.syncclip.server.app.plugins.configureSerialization
import io.github.slavikjunior.syncclip.server.app.plugins.configureStatusPages
import io.github.slavikjunior.syncclip.server.app.plugins.configureWebSockets
import io.github.slavikjunior.syncclip.server.app.wiring.configureRouting
import io.github.slavikjunior.syncclip.server.core.config.AppConfig
import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.github.slavikjunior.syncclip.server.core.security.JwtVerifier
import io.github.slavikjunior.syncclip.server.feature.sync.domain.SessionRegistry
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin

/**
 * Корень приложения: DI-граф, плагины, роуты, graceful shutdown (§13.5).
 * Порядок плагинов важен: сериализация и лимиты — до роутов, StatusPages — после auth.
 * Зависимости, нужные при остановке, резолвятся заранее: на `ApplicationStopping`
 * Koin-скоуп уже закрывается, и `get()` внутри колбэка бросил бы ClosedScopeException.
 */
fun Application.module(config: AppConfig, databaseFactory: DatabaseFactory) {
    install(Koin) {
        modules(serverModule(config, databaseFactory))
    }

    configureSerialization(get())
    configureHttpPlugins(config)
    configureWebSockets(config)
    configureAuthentication(config, get<JwtVerifier>(), get())
    configureStatusPages(get())
    configureRouting()

    val scheduler = get<MaintenanceScheduler>()
    val sessionRegistry = get<SessionRegistry>()
    val pushScope = get<CoroutineScope>()
    scheduler.start(this)

    monitor.subscribe(ApplicationStopping) {
        // Push-корутины не должны пережить приложение и упереться в закрытый пул (§7.5).
        pushScope.cancel()
        scheduler.stop()
        runBlocking { sessionRegistry.shutdownAll() }
    }
    monitor.subscribe(ApplicationStopped) {
        runCatching { databaseFactory.close() }
    }
}
