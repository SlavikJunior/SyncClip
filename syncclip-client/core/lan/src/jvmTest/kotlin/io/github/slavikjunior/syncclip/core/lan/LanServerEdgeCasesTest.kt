package io.github.slavikjunior.syncclip.core.lan

import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Поведение реального TCP-слоя при сбоях: мусор в кадре, исключение в
 * обработчике, сбой `accept`, много одновременных пиров.
 *
 * LAN-приём не должен умирать навсегда из-за одного плохого соединения:
 * mDNS продолжает анонсировать порт, и пиры будут стучаться в мёртвый сервер.
 */
internal class LanServerEdgeCasesTest {

    @Test
    fun `garbage frame closes only that connection`() = runBlocking {
        val handled = java.util.concurrent.atomic.AtomicInteger()
        val server = JavaTcpLanServer()
        val port = server.start { stream ->
            handled.incrementAndGet()
            val frame = LanFrames.read(stream)
            if (frame != null) LanFrames.write(stream, "ACK:$frame")
        }
        try {
            // Заголовок длины больше лимита: сервер обязан закрыть связь.
            Socket("127.0.0.1", port).use { socket ->
                socket.getOutputStream().write(byteArrayOf(0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
                socket.getOutputStream().flush()
                socket.soTimeout = 2_000
                assertEquals(-1, socket.getInputStream().read(), "сервер должен закрыть соединение")
            }

            // Следующее соединение обслуживается как обычно.
            val stream = assertNotNull(JavaTcpLanDialer().dial("127.0.0.1", port, 1_000))
            try {
                LanFrames.write(stream, "hello")
                stream.setReadTimeout(2_000)
                assertEquals("ACK:hello", LanFrames.read(stream))
            } finally {
                stream.close()
            }
            assertEquals(2, handled.get())
        } finally {
            server.stop()
        }
    }

    @Test
    fun `handler exception does not stop the server`() = runBlocking {
        val server = JavaTcpLanServer()
        val port = server.start { stream ->
            if (LanFrames.read(stream) == "boom") error("handler failure")
            LanFrames.write(stream, "ok")
        }
        try {
            val broken = assertNotNull(JavaTcpLanDialer().dial("127.0.0.1", port, 1_000))
            LanFrames.write(broken, "boom")
            broken.close()

            val stream = assertNotNull(JavaTcpLanDialer().dial("127.0.0.1", port, 1_000))
            try {
                LanFrames.write(stream, "ping")
                stream.setReadTimeout(2_000)
                assertEquals("ok", LanFrames.read(stream))
            } finally {
                stream.close()
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun `transient accept failure does not kill the listener`() = runBlocking {
        val server = JavaTcpLanServer { FlakyServerSocket(failures = 2) }
        val port = server.start { stream ->
            if (LanFrames.read(stream) != null) LanFrames.write(stream, "alive")
        }
        try {
            // Два первых accept падают; третий должен обслужить клиента.
            val answer = withTimeout(5_000) {
                repeat(40) {
                    val stream = JavaTcpLanDialer().dial("127.0.0.1", port, 500)
                    if (stream != null) {
                        try {
                            LanFrames.write(stream, "ping")
                            stream.setReadTimeout(500)
                            val frame = LanFrames.read(stream)
                            if (frame != null) return@withTimeout frame
                        } finally {
                            stream.close()
                        }
                    }
                    delay(50)
                }
                null
            }

            assertEquals("alive", answer)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `concurrent peers are served in parallel`() = runBlocking {
        val server = JavaTcpLanServer()
        val port = server.start { stream ->
            val frame = LanFrames.read(stream)
            if (frame != null) LanFrames.write(stream, "ACK:$frame")
        }
        try {
            val dialer = JavaTcpLanDialer()
            val results = withTimeout(10_000) {
                (1..16).map { index ->
                    async(kotlinx.coroutines.Dispatchers.IO) {
                        val stream = dialer.dial("127.0.0.1", port, 2_000) ?: return@async "no stream"
                        try {
                            LanFrames.write(stream, "p$index")
                            stream.setReadTimeout(2_000)
                            LanFrames.read(stream)
                        } finally {
                            stream.close()
                        }
                    }
                }.awaitAll()
            }

            assertEquals((1..16).map { "ACK:p$it" }, results)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `peer that closes without sending a frame is released`() = runBlocking {
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        val server = JavaTcpLanServer()
        val port = server.start { stream ->
            LanFrames.read(stream)
            finished.set(true)
        }
        try {
            Socket("127.0.0.1", port).use { socket ->
                socket.getOutputStream().flush()
            }

            withTimeout(5_000) {
                while (!finished.get()) delay(20)
            }
            assertTrue(finished.get(), "обработчик обязан завершиться на EOF")
        } finally {
            server.stop()
        }
    }

    @Test
    fun `dial to a stopped server fails instead of hanging`() = runBlocking {
        val server = JavaTcpLanServer()
        val port = server.start { }
        server.stop()

        assertNull(JavaTcpLanDialer().dial("127.0.0.1", port, 300))
    }
}

/** `ServerSocket`, у которого первые [failures] вызовов `accept` падают. */
private class FlakyServerSocket(private var failures: Int) : ServerSocket() {

    override fun accept(): Socket {
        if (failures > 0) {
            failures--
            throw IOException("transient accept failure")
        }
        return super.accept()
    }
}
