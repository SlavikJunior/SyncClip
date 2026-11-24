package io.github.slavikjunior.syncclip.server.app.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.encoder.EncoderBase
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * JSON-encoder для Logback без внешних зависимостей (§13.1). Значения
 * редактируются: секреты не попадают в stdout даже при ошибке в вызывающем коде.
 */
class JsonLogEncoder : EncoderBase<ILoggingEvent>() {

    override fun headerBytes(): ByteArray? = null

    override fun footerBytes(): ByteArray? = null

    override fun encode(event: ILoggingEvent): ByteArray {
        val payload = buildJsonObject {
            put("ts", Instant.ofEpochMilli(event.timeStamp).toString())
            put("level", event.level.levelStr)
            put("logger", event.loggerName)
            put("thread", event.threadName)
            put("msg", redact(event.formattedMessage))
            // MDC может быть не инициализирован (например, в юнит-тестах).
            runCatching { event.mdcPropertyMap }.getOrDefault(emptyMap()).forEach { (key, value) ->
                if (key.isNotBlank()) put(key, redact(value))
            }
            event.throwableProxy?.let { throwable ->
                put("exception", throwable.className)
                put("exceptionMessage", redact(throwable.message ?: ""))
            }
        }
        return (payload.toString() + "\n").toByteArray(Charsets.UTF_8)
    }

    private fun redact(value: String): String =
        REDACTORS.fold(value) { acc, redactor -> redactor.replace(acc, "$1=***") }
            .let { JSON_REDACTOR.replace(it, "$1\"***\"") }

    private companion object {
        val REDACTORS = listOf(
            Regex("(?i)\\b(password|refreshToken|accessToken|encryptedPayload|encryptedCanary|inviteToken)\\s*=\\s*[^\\s&,\"}]+"),
            Regex("(?i)(bearer)\\s+[A-Za-z0-9._\\-]+"),
        )

        /** JSON-форма `{"refreshToken":"secret"}` (LOW-05). */
        val JSON_REDACTOR = Regex(
            "(?i)(\"(?:password|refreshToken|accessToken|encryptedPayload|encryptedCanary|inviteToken|token)\"\\s*:\\s*)\"[^\"]*\"",
        )
    }
}
