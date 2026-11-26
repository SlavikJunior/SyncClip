package io.github.slavikjunior.syncclip.core.lan

import java.io.IOException
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Сокет `java.net` (Desktop и Android) в терминах [LanRawStream]. */
internal class JavaLanRawStream(
    private val socket: Socket,
) : LanRawStream {

    private val input = socket.getInputStream()
    private val output = socket.getOutputStream()
    private val writeMutex = Mutex()

    override suspend fun readFully(size: Int): ByteArray? = withContext(Dispatchers.IO) {
        val buffer = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = try {
                input.read(buffer, offset, size - offset)
            } catch (_: IOException) {
                -1
            }
            if (read < 0) return@withContext null
            offset += read
        }
        buffer
    }

    override suspend fun write(bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                output.write(bytes)
                output.flush()
            }
        }
    }

    override suspend fun setReadTimeout(millis: Long) {
        withContext(Dispatchers.IO) {
            socket.soTimeout = millis.toInt().coerceAtLeast(1)
        }
    }

    override suspend fun close() {
        // Пир мог закрыть соединение первым — это штатное завершение.
        runCatching { socket.close() }
    }
}
