package io.github.slavikjunior.syncclip.core.lan

import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** TCP-клиент на `java.net.Socket` (Desktop и Android). */
internal class JavaTcpLanDialer : LanDialer {

    override suspend fun dial(
        host: String,
        port: Int,
        timeoutMillis: Long,
    ): LanRawStream? = withContext(Dispatchers.IO) {
        val budget = timeoutMillis.toInt().coerceAtLeast(1)
        runCatching {
            val socket = Socket()
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), budget)
            socket.soTimeout = budget
            JavaLanRawStream(socket)
        }.getOrNull()
    }
}
