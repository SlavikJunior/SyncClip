package io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository

import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.IncomingClipboardAction
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.IncomingClipboardPacket

/**
 * Приём пакета из любого транспорта (LAN или сервер).
 *
 * Транспорт не решает, что делать с текстом: дедупликация, сохранение в историю
 * и вставка — одна точка, иначе LAN и WebSocket начнут расходиться в поведении.
 */
internal interface ClipboardIncomingSink {

    suspend fun receive(packet: IncomingClipboardPacket): IncomingClipboardAction
}
