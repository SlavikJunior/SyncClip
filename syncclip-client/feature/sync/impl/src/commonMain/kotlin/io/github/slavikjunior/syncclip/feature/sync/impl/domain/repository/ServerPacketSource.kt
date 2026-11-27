package io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository

import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncPacket
import kotlinx.coroutines.flow.Flow

/**
 * Пакеты, которые должен отправить WAN-транспорт.
 *
 * Заполняется LAN-транспортом: если хотя бы один пир подтвердил доставку за
 * 300 мс, пакет на сервер не уходит (FR-2.6); иначе — уходит через это
 * представление.
 */
internal interface ServerPacketSource {

    fun serverPackets(): Flow<SyncPacket>
}
