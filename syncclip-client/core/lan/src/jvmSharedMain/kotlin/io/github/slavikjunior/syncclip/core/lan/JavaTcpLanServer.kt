package io.github.slavikjunior.syncclip.core.lan

import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TCP-сервер на `java.net.ServerSocket` (Desktop и Android).
 *
 * Порт выбирает ОС (`bind(0)`) — фиксированный порт конфликтовал бы с уже
 * запущенным клиентом, а mDNS всё равно публикует фактический.
 *
 * @param serverSocketFactory подменяется в тестах: сбойный `accept` не должен
 *   останавливать приём навсегда.
 */
internal class JavaTcpLanServer(
    private val serverSocketFactory: () -> ServerSocket = { ServerSocket() },
) : LanServer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null

    @Volatile
    private var serverSocket: ServerSocket? = null

    override suspend fun start(handle: suspend (LanRawStream) -> Unit): Int =
        withContext(Dispatchers.IO) {
            stopInternal()
            val server = serverSocketFactory().apply {
                reuseAddress = true
                bind(InetSocketAddress(0))
            }
            serverSocket = server
            acceptJob = scope.launch {
                while (isActive) {
                    val client = try {
                        server.accept()
                    } catch (_: IOException) {
                        // Сбой отдельного accept (EMFILE, сброс соединения на
                        // уровне ОС) не должен навсегда выключать LAN-приём:
                        // иначе mDNS продолжит анонсировать «живой» порт.
                        if (server.isClosed) break
                        delay(ACCEPT_RETRY_DELAY_MILLIS)
                        continue
                    }
                    // Соединение — отдельный ребёнок SupervisorJob: падение
                    // обработчика одного пира не должно отменять acceptJob.
                    scope.launch {
                        // `getInputStream()` бросает, если пир закрыл связь сразу
                        // после accept (сканирование портов): сокет закрываем,
                        // иначе теряется дескриптор.
                        val stream = try {
                            JavaLanRawStream(client)
                        } catch (_: Exception) {
                            runCatching { client.close() }
                            return@launch
                        }
                        try {
                            handle(stream)
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            // Ошибка обработчика одного пира не должна уходить в
                            // scope без обработчика: это UncaughtException → краш
                            // процесса на Android.
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

    private companion object {
        const val ACCEPT_RETRY_DELAY_MILLIS = 50L
    }
}
