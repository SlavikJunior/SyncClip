package io.github.slavikjunior.syncclip.server.app

import io.github.slavikjunior.syncclip.server.app.logging.LogConfigurator
import io.github.slavikjunior.syncclip.server.core.config.AppConfig
import io.github.slavikjunior.syncclip.server.core.config.AppEnvironment
import io.github.slavikjunior.syncclip.server.core.config.ConfigLoader
import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory

/**
 * Точка входа: конфиг → логи → БД/миграции → Netty.
 * Fail-fast конфига происходит до открытия порта (§5.3).
 */
fun main() {
    val config = ConfigLoader.load()
    LogConfigurator.configure(config.observability)

    val logger = LoggerFactory.getLogger("SyncClip")
    logger.info(
        "starting env={} port={} push={}",
        config.environment,
        config.server.port,
        config.push.provider,
    )

    val databaseFactory = DatabaseFactory(config.database)
    try {
        if (config.database.migrateOnStart) {
            databaseFactory.migrate()
            logger.info("migrations applied")
            if (config.environment == AppEnvironment.PROD) {
                logger.warn("DB_MIGRATE_ON_START=true in PROD: allowed for a single-instance MVP")
            }
        } else {
            databaseFactory.refreshMigrationStatus()
        }
        startServer(config, databaseFactory)
    } catch (e: Exception) {
        logger.error("startup failed", e)
        databaseFactory.close()
        throw e
    }
}

private fun startServer(config: AppConfig, databaseFactory: DatabaseFactory) {
    val server = embeddedServer(
        factory = Netty,
        environment = applicationEnvironment(),
        configure = {
            connectors.add(
                EngineConnectorBuilder().apply {
                    host = config.server.host
                    port = config.server.port
                },
            )
            shutdownGracePeriod = config.server.gracefulShutdownMillis
            shutdownTimeout = config.server.gracefulShutdownMillis
        },
        module = { module(config, databaseFactory) },
    )
    server.start(wait = true)
}
