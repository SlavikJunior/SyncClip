package io.github.slavikjunior.syncclip.feature.sync.impl.data.transport

import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.IncomingClipboardPacket
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardIncomingSink
import io.github.slavikjunior.syncclip.shared.crypto.CryptoException
import io.github.slavikjunior.syncclip.shared.crypto.PayloadCipher
import io.github.slavikjunior.syncclip.shared.crypto.PayloadHash

/**
 * Общая обработка входящего пакета для LAN и WAN.
 *
 * Транспорт не дублирует правила: своё событие не применяется, расшифровка и
 * сверка хеша обязательны, решение «сохранить/вставить/игнорировать» принимает
 * [ClipboardIncomingSink].
 *
 * @return `true`, если пакет принят и его нужно подтвердить ACK.
 */
internal class IncomingPacketProcessor(
    private val cipher: PayloadCipher,
    private val sink: ClipboardIncomingSink,
) {

    suspend fun apply(
        eventId: String,
        originDeviceId: String,
        encryptedPayload: String,
        payloadHash: String,
        timestamp: Long,
        key: ByteArray,
        selfDeviceId: String,
    ): Boolean {
        if (originDeviceId == selfDeviceId) return false

        val text = try {
            cipher.decrypt(encryptedPayload, key)
        } catch (_: CryptoException) {
            return false
        } catch (_: IllegalArgumentException) {
            // Ключ неверной длины (повреждённое хранилище) — пакет не наш.
            // Такое исключение не должно доходить до receiveLoop: транспорт
            // принял бы его за обрыв и переподключался бы на каждом кадре.
            return false
        }
        if (PayloadHash.of(text) != payloadHash) return false

        sink.receive(
            IncomingClipboardPacket(
                eventId = eventId,
                originDeviceId = originDeviceId,
                text = text,
                copiedAtMillis = timestamp,
            ),
        )
        return true
    }
}
