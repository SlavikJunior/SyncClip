package io.github.slavikjunior.syncclip.server.app.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import io.github.slavikjunior.syncclip.server.core.config.ObservabilityConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Программная настройка Logback: JSON по умолчанию, plain-паттерн по `LOG_JSON=false`.
 * Ротация не нужна — пишем в stdout, сборщик снаружи (§13.1).
 */
object LogConfigurator {

    fun configure(observability: ObservabilityConfig) {
        val context = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return
        context.reset()

        val encoder: ch.qos.logback.core.encoder.Encoder<ILoggingEvent> =
            if (observability.jsonLogs) {
                JsonLogEncoder().apply {
                    this.context = context
                    start()
                }
            } else {
                PatternLayoutEncoder().apply {
                    this.context = context
                    pattern = "%d{HH:mm:ss.SSS} %-5level [%thread] %logger{32} - %msg%n"
                    start()
                }
            }

        val appender = ConsoleAppender<ILoggingEvent>().apply {
            this.context = context
            name = "stdout"
            this.encoder = encoder
            start()
        }

        context.getLogger(Logger.ROOT_LOGGER_NAME).apply {
            level = Level.toLevel(observability.logLevel, Level.INFO)
            addAppender(appender)
        }
        // Exposed на DEBUG печатает SQL с параметрами (email, password_hash) — SEC-12.
        context.getLogger("Exposed").level = Level.WARN
        context.getLogger("com.zaxxer.hikari").level = Level.WARN
    }
}
