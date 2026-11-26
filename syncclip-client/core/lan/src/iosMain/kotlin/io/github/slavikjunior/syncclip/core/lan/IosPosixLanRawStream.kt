package io.github.slavikjunior.syncclip.core.lan

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.posix.SO_RCVTIMEO
import platform.posix.SOL_SOCKET
import platform.posix.close
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.timeval

/** POSIX-сокет в терминах [LanRawStream]. */
@OptIn(ExperimentalForeignApi::class)
internal class IosPosixLanRawStream(
    private val descriptor: Int,
) : LanRawStream {

    override suspend fun readFully(size: Int): ByteArray? = withContext(Dispatchers.Default) {
        val buffer = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = buffer.usePinned { pinned ->
                recv(descriptor, pinned.addressOf(offset), (size - offset).toULong(), 0)
            }
            if (read <= 0) return@withContext null
            offset += read.toInt()
        }
        buffer
    }

    override suspend fun write(bytes: ByteArray) {
        withContext(Dispatchers.Default) {
            bytes.usePinned { pinned ->
                var offset = 0
                while (offset < bytes.size) {
                    val written = send(
                        descriptor,
                        pinned.addressOf(offset),
                        (bytes.size - offset).toULong(),
                        0,
                    )
                    if (written <= 0) return@withContext
                    offset += written.toInt()
                }
            }
        }
    }

    override suspend fun setReadTimeout(millis: Long) {
        withContext(Dispatchers.Default) {
            memScoped {
                val timeout = alloc<timeval>()
                timeout.tv_sec = millis / 1_000
                timeout.tv_usec = ((millis % 1_000) * 1_000).toInt()
                setsockopt(
                    descriptor,
                    SOL_SOCKET,
                    SO_RCVTIMEO,
                    timeout.ptr,
                    sizeOf<timeval>().toUInt(),
                )
            }
        }
    }

    override suspend fun close() {
        close(descriptor)
    }
}
