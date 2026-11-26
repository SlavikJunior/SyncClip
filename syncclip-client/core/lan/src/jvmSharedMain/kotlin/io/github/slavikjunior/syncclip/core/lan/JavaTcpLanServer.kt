package io.github.slavikjunior.syncclip.core.lan

import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TCP-сервер на `java.net.ServerSocket` (Desktop и Android).
 *
 * Порт выбирает ОС (`bind(0)`) — фиксированный порт конфликтовал бы с уже
 * запущенным клиентом, а mDNS всё равно публикует фактический.
 */
internal class JavaTcpLanServer : LanServer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null

    @Volatile
    private var serverSocket: ServerSocket? = null

    override suspend fun start(handle: suspend (LanRawStream) -> Unit): Int =
        withContext(Dispatchers.IO) {
            stopInternal()
            val server = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(0))
            }
            serverSocket = server
            acceptJob = scope.launch {
                while (isActive) {
                    val client = runCatching { server.accept() }.getOrNull() ?: break
                    launch {
                        val stream = JavaLanRawStream(client)
                        try {
                            handle(stream)
                        } finally {
                            stream.close()
                        }
                    }
                }
            }
            server.localPort
        }

    override suspend fun stop() {
        withContext(Dispatchers.IO) { stopInternal() }
    }

    private fun stopInternal() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }
}
