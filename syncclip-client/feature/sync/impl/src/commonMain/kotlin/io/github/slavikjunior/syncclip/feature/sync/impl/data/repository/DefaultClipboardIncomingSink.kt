package io.github.slavikjunior.syncclip.feature.sync.impl.data.repository

import io.github.slavikjunior.syncclip.core.clipboard.ClipboardGateway
import io.github.slavikjunior.syncclip.core.clipboard.EpochClock
import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettingsStore
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.IncomingClipboardAction
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.IncomingClipboardPacket
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardHistoryWriter
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardIncomingSink
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.DeduplicateEventUseCase
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.ResolveIncomingClipboardUseCase

/**
 * Единая обработка входящего пакета для всех транспортов (FR-2.5, FR-2.8).
 *
 * Дубль определяется по `eventId` до любых побочных эффектов: если этого не
 * сделать, LAN и сервер применят один пакет дважды.
 */
internal class DefaultClipboardIncomingSink(
    private val deduplicate: DeduplicateEventUseCase,
    private val resolve: ResolveIncomingClipboardUseCase,
    private val gateway: ClipboardGateway,
    private val historyWriter: ClipboardHistoryWriter,
    private val settings: ClipboardSettingsStore,
    private val clock: EpochClock,
) : ClipboardIncomingSink {

    override suspend fun receive(packet: IncomingClipboardPacket): IncomingClipboardAction {
        if (deduplicate(packet.eventId)) return IncomingClipboardAction.IGNORE

        val current = settings.current()
        val action = resolve(
            isOnline = current.isOnline,
            autoInsert = current.autoInsert,
            nowMillis = clock.nowMillis(),
            lastLocalCaptureMillis = gateway.lastLocalCaptureMillis(),
        )
        historyWriter.save(
            text = packet.text,
            timestampMillis = packet.copiedAtMillis,
            sourceDeviceId = packet.originDeviceId,
        )
        if (action == IncomingClipboardAction.SAVE_AND_INSERT) {
            gateway.insert(packet.text)
        }
        return action
    }
}
