package io.github.slavikjunior.syncclip.core.lan

import java.net.ServerSocket
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Loopback-проверка реального TCP-слоя: сервер, клиент, кадры. */
internal class JavaTcpLanLoopbackTest {

    @Test
    fun `server receives a frame and replies`() = runBlocking {
        val server = JavaTcpLanServer()
        val port = server.start { stream ->
            val frame = LanFrames.read(stream)
            if (frame != null) LanFrames.write(stream, "ACK:$frame")
        }
        try {
            val stream = assertNotNull(
                JavaTcpLanDialer().dial("127.0.0.1", port, timeoutMillis = 1_000),
                "клиент не подключился к loopback-серверу",
            )
            try {
                LanFrames.write(stream, "hello")
                stream.setReadTimeout(1_000)
                assertEquals("ACK:hello", LanFrames.read(stream))
            } finally {
                stream.close()
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun `dial fails when nobody listens`() = runBlocking {
        val closedPort = ServerSocket(0).use { it.localPort }

        assertNull(JavaTcpLanDialer().dial("127.0.0.1", closedPort, timeoutMillis = 200))
    }

    @Test
    fun `server restarts on a new port`() = runBlocking {
        val server = JavaTcpLanServer()
        val first = server.start { }
        val second = runCatching {
            server.start { }
        }.getOrDefault(-1)
        server.stop()

        // Повторный start сначала останавливает предыдущий сервер.
        assertEquals(true, first > 0)
        assertEquals(true, second > 0)
    }
}
