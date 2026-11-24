package io.github.slavikjunior.syncclip.server.app

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import io.github.slavikjunior.syncclip.server.app.logging.JsonLogEncoder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonLogEncoderTest {

    private val encoder = JsonLogEncoder().apply {
        context = LoggerContext()
        start()
    }

    private fun encode(message: String): String {
        val context = LoggerContext()
        val logger = context.getLogger("test")
        val event = LoggingEvent(logger.name, logger, Level.INFO, message, null, null).apply {
            threadName = "test-thread"
            timeStamp = 1_700_000_000_000
        }
        return String(encoder.encode(event), Charsets.UTF_8)
    }

    @Test
    fun `output is a single json line with fixed fields`() {
        val line = encode("hello").trim()
        assertTrue(line.startsWith("{") && line.endsWith("}"))
        assertTrue(line.contains("\"level\":\"INFO\""))
        assertTrue(line.contains("\"msg\":\"hello\""))
        assertFalse(line.contains("\n"))
    }

    @Test
    fun `secrets are redacted`() {
        val line = encode("login refreshToken=abc.def.ghi password=hunter2 Authorization: Bearer aaa.bbb.ccc")
        assertFalse(line.contains("hunter2"))
        assertFalse(line.contains("abc.def.ghi"))
        assertFalse(line.contains("aaa.bbb.ccc"))
        assertTrue(line.contains("***"))
    }

    @Test
    fun `json shaped secrets are redacted too`() {
        val line = encode(
            """body={"refreshToken":"abc.def","password":"hunter2","encryptedPayload":"AAAA"}""",
        )
        assertFalse(line.contains("abc.def"))
        assertFalse(line.contains("hunter2"))
        assertFalse(line.contains("AAAA"))
        // В выводе JSON-строка экранирована, поэтому проверяем количество редакций.
        assertTrue(line.contains("***"))
    }
}
