package io.github.slavikjunior.syncclip.core.lan

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.posix.AF_INET
import platform.posix.EINPROGRESS
import platform.posix.F_GETFL
import platform.posix.F_SETFL
import platform.posix.O_NONBLOCK
import platform.posix.POLLOUT
import platform.posix.SOCK_STREAM
import platform.posix.SO_ERROR
import platform.posix.SOL_SOCKET
import platform.posix.addrinfo
import platform.posix.close
import platform.posix.connect
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.getsockopt
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.socket

/**
 * TCP-клиент для iOS.
 *
 * `connect` переводится в неблокирующий режим и ждёт `poll(POLLOUT)` не дольше
 * бюджета: иначе системный connect мог бы висеть до 75 секунд и ломать
 * требование ACK ≤ 300 мс.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosPosixLanDialer : LanDialer {

    override suspend fun dial(
        host: String,
        port: Int,
        timeoutMillis: Long,
    ): LanRawStream? = withContext(Dispatchers.Default) {
        if (timeoutMillis <= 0) return@withContext null
        memScoped {
            val hints = alloc<addrinfo>()
            hints.ai_family = AF_INET
            hints.ai_socktype = SOCK_STREAM
            hints.ai_protocol = 0
            val result = alloc<kotlinx.cinterop.CPointerVar<addrinfo>>()
            if (getaddrinfo(host, port.toString(), hints.ptr, result.ptr) != 0) {
                return@memScoped null
            }
            val first = result.value
            try {
                var candidate = first
                while (candidate != null) {
                    val address = candidate.pointed
                    val descriptor = socket(address.ai_family, address.ai_socktype, address.ai_protocol)
                    if (descriptor >= 0) {
                        if (connectWithTimeout(descriptor, candidate, timeoutMillis)) {
                            return@memScoped IosPosixLanRawStream(descriptor)
                        }
                        close(descriptor)
                    }
                    candidate = address.ai_next
                }
                null
            } finally {
                freeaddrinfo(first)
            }
        }
    }

    private fun connectWithTimeout(
        descriptor: Int,
        address: kotlinx.cinterop.CPointer<addrinfo>,
        timeoutMillis: Long,
    ): Boolean {
        val flags = fcntl(descriptor, F_GETFL, 0)
        fcntl(descriptor, F_SETFL, flags or O_NONBLOCK)

        val result = connect(descriptor, address.pointed.ai_addr, address.pointed.ai_addrlen)
        if (result != 0) {
            if (errno != EINPROGRESS) return false
            memScoped {
                val pollFd = alloc<pollfd>()
                pollFd.fd = descriptor
                pollFd.events = POLLOUT.toShort()
                if (poll(pollFd.ptr, 1u, timeoutMillis.toInt()) <= 0) return false
                val error = alloc<kotlinx.cinterop.IntVar>()
                val length = alloc<kotlinx.cinterop.UIntVar>()
                length.value = sizeOf<kotlinx.cinterop.IntVar>().toUInt()
                if (getsockopt(descriptor, SOL_SOCKET, SO_ERROR, error.ptr, length.ptr) != 0) {
                    return false
                }
                if (error.value != 0) return false
            }
        }
        fcntl(descriptor, F_SETFL, flags)
        return true
    }

}
