package io.github.slavikjunior.syncclip.feature.sync.impl.data.repository

import io.github.slavikjunior.syncclip.core.clipboard.ClipboardGateway
import io.github.slavikjunior.syncclip.core.clipboard.PendingShareSource
import io.github.slavikjunior.syncclip.core.clipboard.EpochClock
import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettingsStore
import io.github.slavikjunior.syncclip.feature.sync.api.ClipboardCaptureStarter
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.LocalCaptureVerdict
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncPacket
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardCaptureFeed
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardHistoryWriter
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardPacketSource
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.AcceptLocalCaptureUseCase
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.DebounceClipboardUseCase
import kotlin.uuid.Uuid
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

/**
 * Конвейер локального копирования: буфер → правила → история → сеть.
 *
 * Порядок важен: в историю попадает всё принятое, даже в Offline (FR-2.9);
 * в исходящий поток — только то, что разрешено отправлять сейчас. Потоки
 * горячие и с `DROP_OLDEST`: системный callback не должен ждать подписчика.
 */
internal class ClipboardCaptureRepository(
    private val gateway: ClipboardGateway,
    private val pendingShares: PendingShareSource,
    private val historyWriter: ClipboardHistoryWriter,
    private val settings: ClipboardSettingsStore,
    private val accept: AcceptLocalCaptureUseCase,
    private val debounce: DebounceClipboardUseCase,
    private val clock: EpochClock,
) : ClipboardPacketSource, ClipboardCaptureFeed, ClipboardCaptureStarter {

    private val capturedEvents = MutableSharedFlow<SyncPacket>(
        extraBufferCapacity = BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val outgoingEvents = MutableSharedFlow<SyncPacket>(
        extraBufferCapacity = BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun captures(): Flow<SyncPacket> = capturedEvents.asSharedFlow()

    override fun outgoingPackets(): Flow<SyncPacket> = outgoingEvents.asSharedFlow()

    override suspend fun run() {
        // Уже отправленные расширением тексты — только в историю, без повторной
        // отправки: иначе получатели увидели бы дубль с новым eventId.
        pendingShares.takeAlreadySent().forEach { text ->
            if (accept(text) == LocalCaptureVerdict.ACCEPT) {
                historyWriter.save(
                    text = text,
                    timestampMillis = clock.nowMillis(),
                    sourceDeviceId = null,
                )
            }
        }
        // Неотправленные (не было сессии/сети) проходят обычный путь.
        pendingShares.take().forEach { text -> captureText(text) }

        debounce(gateway.localCopies().map { capture ->
            SyncPacket(
                eventId = Uuid.random().toString(),
                text = capture.text,
                copiedAtMillis = capture.capturedAtMillis,
            )
        }).collect { packet -> onCaptured(packet) }
    }

    override suspend fun captureCurrent(): Boolean {
        val text = gateway.currentText() ?: return false
        return captureText(text)
    }

    override suspend fun captureText(text: String): Boolean = onCaptured(
        SyncPacket(
            eventId = Uuid.random().toString(),
            text = text,
            copiedAtMillis = clock.nowMillis(),
        ),
    )

    private suspend fun onCaptured(packet: SyncPacket): Boolean {
        if (accept(packet.text) != LocalCaptureVerdict.ACCEPT) return false

        historyWriter.save(
            text = packet.text,
            timestampMillis = packet.copiedAtMillis,
            sourceDeviceId = null,
        )
        capturedEvents.emit(packet)
        if (settings.current().isOnline) {
            outgoingEvents.emit(packet)
        }
        return true
    }

    private companion object {
        const val BUFFER_CAPACITY = 64
    }
}
