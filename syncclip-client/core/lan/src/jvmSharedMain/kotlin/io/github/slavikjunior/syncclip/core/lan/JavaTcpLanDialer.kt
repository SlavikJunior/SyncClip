package io.github.slavikjunior.syncclip.core.lan

import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CancellationException
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
        var socket: Socket? = null
        try {
            val created = Socket()
            socket = created
            created.tcpNoDelay = true
            created.connect(InetSocketAddress(host, port), budget)
            created.soTimeout = budget
            JavaLanRawStream(created)
        } catch (cancel: CancellationException) {
            runCatching { socket?.close() }
            throw cancel
        } catch (_: Exception) {
            // Пир исчез из сети, но остался в mDNS-кэше (или кончились
            // дескрипторы): без закрытия сокета каждая неудачная попытка теряла
            // бы файловый дескриптор, и после нескольких сотен копирований
            // приложение упиралось бы в EMFILE.
            runCatching { socket?.close() }
            null
        }
    }
}
