package io.github.slavikjunior.syncclip.core.lan

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.posix.AF_INET
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_REUSEADDR
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket

/** TCP-сервер на POSIX-сокетах для iOS. */
@OptIn(ExperimentalForeignApi::class)
internal class IosPosixLanServer : LanServer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var acceptJob: Job? = null

    private var descriptor: Int = -1

    override suspend fun start(handle: suspend (LanRawStream) -> Unit): Int =
        withContext(Dispatchers.Default) {
            stopInternal()
            val server = socket(AF_INET, SOCK_STREAM, 0)
            if (server < 0) error("LAN server socket failed")

            memScoped {
                val reuse = alloc<kotlinx.cinterop.IntVar>()
                reuse.value = 1
                setsockopt(
                    server,
                    SOL_SOCKET,
                    SO_REUSEADDR,
                    reuse.ptr,
                    sizeOf<kotlinx.cinterop.IntVar>().toUInt(),
                )

                val address = alloc<sockaddr_in>()
                address.sin_family = AF_INET.convert()
                address.sin_port = 0u.toUShort()
                address.sin_addr.s_addr = 0u
                if (bind(server, address.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt()) != 0) {
                    close(server)
                    error("LAN server bind failed")
                }
                if (listen(server, 8) != 0) {
                    close(server)
                    error("LAN server listen failed")
                }

                val boundLength = alloc<kotlinx.cinterop.UIntVar>()
                boundLength.value = sizeOf<sockaddr_in>().toUInt()
                getsockname(server, address.ptr.reinterpret(), boundLength.ptr)
                val port = swapBytes(address.sin_port).toInt()

                descriptor = server
                acceptJob = scope.launch {
                    while (isActive) {
                        val client = accept(server, null, null)
                        if (client < 0) {
                            // EINTR/EMFILE/ECONNABORTED — разовый сбой, а не
                            // приглашение выключить приём: mDNS продолжает
                            // анонсировать порт, и пиры стучались бы в мёртвый
                            // сервер. Выходим только если сервер остановлен.
                            if (descriptor < 0) break
                            delay(ACCEPT_RETRY_DELAY_MILLIS)
                            continue
                        }
                        launch {
                            val stream = try {
                                IosPosixLanRawStream(client)
                            } catch (_: Exception) {
                                close(client)
                                return@launch
                            }
                            try {
                                handle(stream)
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (_: Exception) {
                                // Сбой обработчика одного пира не должен ронять
                                // scope без CoroutineExceptionHandler.
                            } finally {
                                stream.close()
                            }
                        }
                    }
                }
                port
            }
        }

    override suspend fun stop() {
        withContext(Dispatchers.Default) { stopInternal() }
    }

    private fun stopInternal() {
        acceptJob?.cancel()
        acceptJob = null
        if (descriptor >= 0) {
            close(descriptor)
            descriptor = -1
        }
    }

    private companion object {
        /** Пауза перед повтором `accept` после разового сбоя. */
        const val ACCEPT_RETRY_DELAY_MILLIS = 50L
    }

}

/** htons/ntohs на little-endian: меняем байты местами. */
private fun swapBytes(value: UShort): UShort =
    (((value.toInt() and 0xFF) shl 8) or ((value.toInt() shr 8) and 0xFF)).toUShort()
