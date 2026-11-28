package io.github.slavikjunior.syncclip.feature.sync.impl.data.transport

import io.github.slavikjunior.syncclip.core.clipboard.EpochClock
import io.github.slavikjunior.syncclip.core.clipboard.ClipboardLimits
import io.github.slavikjunior.syncclip.core.clipboard.TargetedClipboardSender
import io.github.slavikjunior.syncclip.core.clipboard.TargetedSendResult
import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettingsStore
import io.github.slavikjunior.syncclip.core.datastore.DeviceIdentityStore
import io.github.slavikjunior.syncclip.core.datastore.SessionRefresher
import io.github.slavikjunior.syncclip.feature.sync.api.SyncTransportStarter
import io.github.slavikjunior.syncclip.feature.sync.impl.data.remote.ClientMessage
import io.github.slavikjunior.syncclip.feature.sync.impl.data.remote.ServerMessage
import io.github.slavikjunior.syncclip.feature.sync.impl.data.remote.SyncSocket
import io.github.slavikjunior.syncclip.feature.sync.impl.data.remote.SyncSocketFactory
import io.github.slavikjunior.syncclip.feature.sync.impl.data.remote.WsCodec
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncConnectionStatus
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncPacket
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardIncomingSink
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ServerPacketSource
import io.github.slavikjunior.syncclip.shared.crypto.E2eeKeyProvider
import io.github.slavikjunior.syncclip.shared.crypto.PayloadHash
import io.github.slavikjunior.syncclip.shared.crypto.PayloadCipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * WebSocket-транспорт: шифрует исходящие копирования и применяет входящие.
 *
 * Ответственность ровно одна — доставка. Правила «что делать с текстом»
 * (дедупликация, авто-вставка, история) остаются в [ClipboardIncomingSink],
 * поэтому LAN-транспорт подключится в ту же точку без дублирования логики.
 *
 * Порядок исходящего пакета (FR-2.5, §8.1):
 * `eventId` → AES-256-GCM(`IV || ciphertext || tag`, base64) → `payloadHash`
 * (SHA-256 открытого текста) → `SYNC_EVENT`.
 *
 * Входящий пакет доверенный только после расшифровки и сверки хеша: иначе
 * повреждённый/подменённый пакет попал бы в историю.
 */
internal class SyncTransport(
    private val socketFactory: SyncSocketFactory,
    private val keyProvider: E2eeKeyProvider,
    private val identity: DeviceIdentityStore,
    private val serverPackets: ServerPacketSource,
    private val incomingSink: ClipboardIncomingSink,
    private val settings: ClipboardSettingsStore,
    private val cipher: PayloadCipher,
    private val clock: EpochClock,
    private val sessionRefresher: SessionRefresher? = null,
    private val backoff: BackoffPolicy = BackoffPolicy(),
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : SyncTransportStarter, TargetedClipboardSender {

    private val processor = IncomingPacketProcessor(cipher, incomingSink)

    private val status = MutableStateFlow(SyncConnectionStatus.LOCKED)
    val connectionStatus: StateFlow<SyncConnectionStatus> = status.asStateFlow()

    /**
     * Адресные пакеты (US-016) идут только через сервер: LAN-пир не знает, кому
     * именно они предназначены, а сервер фильтрует по `targetDeviceIds`.
     */
    private val targetedPackets = MutableSharedFlow<SyncPacket>(
        extraBufferCapacity = TARGETED_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override suspend fun send(text: String, targetDeviceIds: List<String>): TargetedSendResult {
        if (text.isBlank() || ClipboardLimits.utf8Size(text) > ClipboardLimits.MAX_TEXT_BYTES) {
            return TargetedSendResult.Empty
        }
        if (!settings.current().isOnline || status.value != SyncConnectionStatus.ONLINE) {
            return TargetedSendResult.Offline
        }
        targetedPackets.emit(
            SyncPacket(
                eventId = Uuid.random().toString(),
                text = text,
                copiedAtMillis = clock.nowMillis(),
                isProgrammatic = true,
                targetDeviceIds = targetDeviceIds,
            ),
        )
        return TargetedSendResult.Queued
    }

    override suspend fun run() {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            // FR-2.9: в Offline соединение не держим — сервер не получает
            // входящие, а локальные события не уходят в сеть.
            if (!settings.current().isOnline) {
                status.value = SyncConnectionStatus.OFFLINE
                settings.state.first { it.isOnline }
                continue
            }

            val key = keyProvider.key()
            if (key == null) {
                status.value = SyncConnectionStatus.LOCKED
                delay(LOCK_POLL_MILLIS)
                continue
            }

            // Access-токен живёт час: обновляем пару до рукопожатия, иначе
            // переподключение после простоя получит 401.
            runCatching { sessionRefresher?.ensureFresh() }

            val socket = openSocket()
            if (socket == null) {
                attempt++
                status.value = if (attempt >= backoff.maxAttempts) {
                    SyncConnectionStatus.SUSPENDED
                } else {
                    SyncConnectionStatus.OFFLINE
                }
                delay(backoff.delayForAttempt(attempt))
                continue
            }

            // Счётчик неудач сбрасывает только соединение, прожившее достаточно
            // долго: рукопожатие, закрытое сразу, — тоже неудача (см. ниже).
            status.value = SyncConnectionStatus.ONLINE
            val connectedAt = timeSource.markNow()
            var revoked = false
            try {
                val deviceId = identity.identity().deviceId
                coroutineScope {
                    val lastSendMillis = MutableStateFlow(clock.nowMillis())
                    // Живость соединения определяется входящими кадрами:
                    // полуоткрытый TCP не сообщает об обрыве, и без этой метки
                    // транспорт оставался бы ONLINE навсегда.
                    val lastIncomingMillis = MutableStateFlow(clock.nowMillis())
                    val pingJob = launch { pingLoop(socket, lastSendMillis, lastIncomingMillis) }
                    val sendJob = launch { sendLoop(socket, key, deviceId, lastSendMillis) }
                    val offlineWatcher = launch {
                        settings.state.first { !it.isOnline }
                        // Закрытие сокета останавливает receiveLoop, а finally
                        // отменяет остальные дочерние корутины.
                        socket.close()
                    }
                    try {
                        receiveLoop(socket, key, deviceId, lastIncomingMillis)
                    } finally {
                        offlineWatcher.cancel()
                        sendJob.cancel()
                        pingJob.cancel()
                    }
                }
            } catch (_: SessionRevokedException) {
                revoked = true
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // Обрыв соединения — штатный путь: внешний цикл переподключится.
            } finally {
                socket.close()
            }

            if (revoked) {
                status.value = SyncConnectionStatus.SUSPENDED
                return
            }

            // Соединение, закрытое сразу после рукопожатия (отказ сервера,
            // сетевой экран, чужой deviceId), — это такая же неудача, как
            // неудачный connect. Без backoff клиент молотил бы переподключения
            // раз в секунду и никогда не показал бы SUSPENDED (FR-2.3).
            // Пауза самим пользователем (Offline) неудачей не считается.
            val pausedByUser = !settings.current().isOnline
            if (!pausedByUser && connectedAt.elapsedNow() < HEALTHY_CONNECTION_WINDOW) {
                attempt++
                status.value = if (attempt >= backoff.maxAttempts) {
                    SyncConnectionStatus.SUSPENDED
                } else {
                    SyncConnectionStatus.OFFLINE
                }
                delay(backoff.delayForAttempt(attempt))
            } else {
                attempt = 0
                status.value = SyncConnectionStatus.OFFLINE
                delay(RECONNECT_DELAY_MILLIS)
            }
        }
    }

    private suspend fun openSocket(): SyncSocket? = try {
        socketFactory.open()
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        null
    }

    private suspend fun sendLoop(
        socket: SyncSocket,
        key: ByteArray,
        deviceId: String,
        lastSendMillis: MutableStateFlow<Long>,
    ) {
        // Обычные и адресные пакеты идут одним циклом: порядок записи в сокет
        // сохраняется, а шифрование и формат кадра не дублируются.
        merge(serverPackets.serverPackets(), targetedPackets).collect { packet ->
            if (!settings.current().isOnline) return@collect
            val message = ClientMessage.SyncEvent(
                eventId = packet.eventId,
                originDeviceId = deviceId,
                targetDeviceIds = packet.targetDeviceIds,
                encryptedPayload = cipher.encrypt(packet.text, key),
                payloadHash = PayloadHash.of(packet.text),
                timestamp = packet.copiedAtMillis.takeIf { it > 0 } ?: clock.nowMillis(),
            )
            socket.send(WsCodec.encode(message))
            lastSendMillis.value = clock.nowMillis()
        }
    }

    private suspend fun pingLoop(
        socket: SyncSocket,
        lastSendMillis: MutableStateFlow<Long>,
        lastIncomingMillis: MutableStateFlow<Long>,
    ) {
        while (currentCoroutineContext().isActive) {
            delay(PING_INTERVAL_MILLIS)
            val now = clock.nowMillis()
            // Три интервала без единого входящего кадра — соединение мёртвое
            // (Wi-Fi → LTE, NAT-таймаут, «чёрная дыра»). Сервер отвечает на
            // каждый PING, поэтому живое соединение всегда обновляет метку.
            if (now - lastIncomingMillis.value > STALE_CONNECTION_MILLIS) {
                throw StaleConnectionException()
            }
            if (now - lastSendMillis.value < PING_INTERVAL_MILLIS) continue
            socket.send(WsCodec.encode(ClientMessage.Ping(now)))
            lastSendMillis.value = now
        }
    }

    private suspend fun receiveLoop(
        socket: SyncSocket,
        key: ByteArray,
        deviceId: String,
        lastIncomingMillis: MutableStateFlow<Long>,
    ) {
        socket.incoming().collect { frame ->
            // Метка живости — до разбора: PONG, ACK и мусор одинаково
            // доказывают, что соединение ещё живо.
            lastIncomingMillis.value = clock.nowMillis()
            when (val message = WsCodec.decode(frame)) {
                null -> Unit
                is ServerMessage.SyncEvent -> {
                    val applied = processor.apply(
                        eventId = message.eventId,
                        originDeviceId = message.originDeviceId,
                        encryptedPayload = message.encryptedPayload,
                        payloadHash = message.payloadHash,
                        timestamp = message.timestamp,
                        key = key,
                        selfDeviceId = deviceId,
                    )
                    if (applied) {
                        socket.send(WsCodec.encode(ClientMessage.Ack(message.eventId, deviceId)))
                    }
                }
                is ServerMessage.SessionRevoked -> throw SessionRevokedException()
                is ServerMessage.Pong -> Unit
                is ServerMessage.Ack -> Unit
                is ServerMessage.Error -> Unit
            }
        }
    }

    private class SessionRevokedException : Exception()

    /** Соединение не подаёт признаков жизни дольше [STALE_CONNECTION_MILLIS]. */
    private class StaleConnectionException : Exception()

    private companion object {
        const val PING_INTERVAL_MILLIS = 30_000L
        const val RECONNECT_DELAY_MILLIS = 1_000L
        const val LOCK_POLL_MILLIS = 500L

        /** Очередь адресных отправок: пользователь нажимает «Отправить» редко. */
        const val TARGETED_BUFFER_CAPACITY = 16

        /**
         * Сколько молчания терпим до переподключения. Три интервала PING:
         * один потерянный PONG не должен рвать рабочее соединение.
         */
        const val STALE_CONNECTION_MILLIS = 3 * PING_INTERVAL_MILLIS

        /**
         * Соединение, прожившее меньше этого окна, считается неудачной попыткой
         * подключения: иначе сервер, рвущий WebSocket сразу после рукопожатия,
         * вызывал бы бесконечный шторм переподключений без backoff.
         */
        val HEALTHY_CONNECTION_WINDOW: Duration = 10.seconds
    }
}
