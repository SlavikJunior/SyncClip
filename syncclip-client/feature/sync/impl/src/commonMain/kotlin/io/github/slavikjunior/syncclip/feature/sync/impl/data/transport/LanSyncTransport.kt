package io.github.slavikjunior.syncclip.feature.sync.impl.data.transport

import io.github.slavikjunior.syncclip.core.clipboard.EpochClock
import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettingsStore
import io.github.slavikjunior.syncclip.core.datastore.DeviceIdentityStore
import io.github.slavikjunior.syncclip.core.datastore.SessionState
import io.github.slavikjunior.syncclip.core.datastore.SessionStore
import io.github.slavikjunior.syncclip.core.lan.LanAdvertisement
import io.github.slavikjunior.syncclip.core.lan.LanDiscovery
import io.github.slavikjunior.syncclip.core.lan.LanDiscoveryFactory
import io.github.slavikjunior.syncclip.core.lan.LanFrames
import io.github.slavikjunior.syncclip.core.lan.LanPeer
import io.github.slavikjunior.syncclip.core.lan.LanRawStream
import io.github.slavikjunior.syncclip.core.lan.LanServer
import io.github.slavikjunior.syncclip.feature.sync.api.LanTransportStarter
import io.github.slavikjunior.syncclip.feature.sync.impl.data.remote.ClientMessage
import io.github.slavikjunior.syncclip.feature.sync.impl.data.remote.ServerMessage
import io.github.slavikjunior.syncclip.feature.sync.impl.data.remote.WsCodec
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncPacket
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardPacketSource
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ServerPacketSource
import io.github.slavikjunior.syncclip.shared.crypto.E2eeKeyProvider
import io.github.slavikjunior.syncclip.shared.crypto.PayloadCipher
import io.github.slavikjunior.syncclip.shared.crypto.PayloadHash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * LAN P2P: анонс через mDNS/NSD, TCP-сервер и параллельная отправка пирам.
 *
 * Пакет уходит всем найденным устройствам аккаунта одновременно (FR-2.6).
 * Сервер получает его только если ни один пир не подтвердил доставку за
 * [ackTimeoutMillis]; тот же `eventId` используется в обоих каналах, поэтому
 * дубль на получателе отбрасывается дедупликацией (FR-2.5).
 */
internal class LanSyncTransport(
    private val server: LanServer,
    private val discoveryFactory: LanDiscoveryFactory,
    private val dialer: io.github.slavikjunior.syncclip.core.lan.LanDialer,
    private val keyProvider: E2eeKeyProvider,
    private val identity: DeviceIdentityStore,
    private val session: SessionStore,
    private val outgoing: ClipboardPacketSource,
    private val processor: IncomingPacketProcessor,
    private val settings: ClipboardSettingsStore,
    private val cipher: PayloadCipher,
    private val clock: EpochClock,
    private val ackTimeoutMillis: Long = DEFAULT_ACK_TIMEOUT_MILLIS,
) : LanTransportStarter, ServerPacketSource {

    private val fallbackQueue = Channel<SyncPacket>(Channel.UNLIMITED)
    private val fallbackFlow: Flow<SyncPacket> = fallbackQueue.receiveAsFlow()

    override fun serverPackets(): Flow<SyncPacket> = fallbackFlow

    override suspend fun run() {
        val deviceId = identity.identity().deviceId
        val userId = awaitUserId() ?: return

        while (currentCoroutineContext().isActive) {
            settings.state.first { it.isOnline }
            val key = keyProvider.key()
            if (key == null) {
                // Мастер-пароль ещё не введён: LAN-пакет шифровать нечем.
                delay(KEY_POLL_MILLIS)
                continue
            }

            val port = runCatching {
                server.start { stream -> handleConnection(stream, key, deviceId) }
            }.getOrNull()

            if (port == null) {
                // Сервер не поднялся (занят порт/нет сети): всё уходит на сервер.
                forwardAllWhileOnline()
                continue
            }

            val discovery = discoveryFactory.create(
                LanAdvertisement(deviceId = deviceId, userId = userId, port = port),
            )
            val discoveryStarted = runCatching { discovery.start() }.isSuccess
            if (!discoveryStarted) {
                // mDNS недоступен (порт 5353 занят, VPN, firewall): LAN не
                // работает, но WAN обязан продолжать получать пакеты.
                runCatching { discovery.stop() }
                runCatching { server.stop() }
                forwardAllWhileOnline()
                continue
            }
            try {
                coroutineScope {
                    launch {
                        settings.state.first { !it.isOnline }
                        throw OfflineSignal()
                    }
                    outgoing.outgoingPackets().collect { packet ->
                        dispatch(packet, discovery, key, deviceId)
                    }
                }
            } catch (_: OfflineSignal) {
                // Переключились в Offline: закрываем анонс и сервер (FR-2.9).
            } finally {
                discovery.stop()
                server.stop()
            }
        }
    }

    /**
     * @return `true`, если хотя бы один пир подтвердил доставку.
     */
    private suspend fun dispatch(
        packet: SyncPacket,
        discovery: LanDiscovery,
        key: ByteArray,
        deviceId: String,
    ): Boolean {
        if (!settings.current().isOnline) return false
        val peers = discovery.peers.value.filter { it.deviceId != deviceId }
        if (peers.isEmpty()) {
            fallbackQueue.send(packet)
            return false
        }

        val confirmed = coroutineScope {
            peers.map { peer ->
                async { sendToPeer(peer, packet, key, deviceId) }
            }.awaitAll()
        }
        if (confirmed.none { it }) {
            fallbackQueue.send(packet)
            return false
        }
        return true
    }

    private suspend fun sendToPeer(
        peer: LanPeer,
        packet: SyncPacket,
        key: ByteArray,
        deviceId: String,
    ): Boolean {
        val stream = dialer.dial(peer.host, peer.port, ackTimeoutMillis) ?: return false
        return try {
            val startedAt = clock.nowMillis()
            LanFrames.write(
                stream,
                WsCodec.encode(
                    ClientMessage.SyncEvent(
                        eventId = packet.eventId,
                        originDeviceId = deviceId,
                        targetDeviceIds = emptyList(),
                        encryptedPayload = cipher.encrypt(packet.text, key),
                        payloadHash = PayloadHash.of(packet.text),
                        timestamp = packet.copiedAtMillis.takeIf { it > 0 } ?: clock.nowMillis(),
                    ),
                ),
            )

            val remaining = ackTimeoutMillis - (clock.nowMillis() - startedAt)
            if (remaining <= 0) return false
            stream.setReadTimeout(remaining)

            val frame = LanFrames.read(stream) ?: return false
            val ack = WsCodec.decode(frame) as? ServerMessage.Ack ?: return false
            ack.eventId == packet.eventId
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            false
        } finally {
            stream.close()
        }
    }

    private suspend fun handleConnection(
        stream: LanRawStream,
        key: ByteArray,
        deviceId: String,
    ) {
        try {
            if (!settings.current().isOnline) return
            val frame = LanFrames.read(stream) ?: return
            val message = WsCodec.decode(frame) ?: return
            if (message !is ServerMessage.SyncEvent) return

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
                LanFrames.write(
                    stream,
                    WsCodec.encode(ClientMessage.Ack(message.eventId, deviceId)),
                )
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // Битая/оборванная связь: пир повторит или выберет сервер.
        }
    }

    private suspend fun forwardAllWhileOnline() {
        try {
            coroutineScope {
                launch {
                    settings.state.first { !it.isOnline }
                    throw OfflineSignal()
                }
                outgoing.outgoingPackets().collect { packet -> fallbackQueue.send(packet) }
            }
        } catch (_: OfflineSignal) {
            // Offline: отправка прекращается до возврата в Online.
        }
    }

    private suspend fun awaitUserId(): String? {
        while (currentCoroutineContext().isActive) {
            (session.state.value as? SessionState.SignedIn)?.let { return it.session.userId }
            delay(KEY_POLL_MILLIS)
        }
        return null
    }

    private class OfflineSignal : Exception()

    private companion object {
        /** FR-2.6: ACK от пира ждём не дольше 300 мс. */
        const val DEFAULT_ACK_TIMEOUT_MILLIS = 300L
        const val KEY_POLL_MILLIS = 500L
    }
}
