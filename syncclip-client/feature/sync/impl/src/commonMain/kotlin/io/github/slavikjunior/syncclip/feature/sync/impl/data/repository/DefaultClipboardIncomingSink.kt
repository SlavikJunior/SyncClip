package io.github.slavikjunior.syncclip.feature.sync.impl.data.repository

import io.github.slavikjunior.syncclip.core.clipboard.ClipboardGateway
import io.github.slavikjunior.syncclip.core.clipboard.ClipboardLimits
import io.github.slavikjunior.syncclip.core.clipboard.EpochClock
import io.github.slavikjunior.syncclip.core.datastore.AppSettingsStore
import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettingsStore
import io.github.slavikjunior.syncclip.core.platform.IncomingClipNotifier
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.IncomingClipboardAction
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.IncomingClipboardPacket
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardHistoryWriter
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardIncomingSink
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.DeduplicateEventUseCase
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.ResolveIncomingClipboardUseCase
import io.github.slavikjunior.syncclip.shared.model.DeviceDirectory
import io.github.slavikjunior.syncclip.shared.model.deviceName


/**
 * Единая обработка входящего пакета для всех транспортов (FR-2.5, FR-2.8).
 *
 * Дубль определяется по `eventId` до любых побочных эффектов: если этого не
 * сделать, LAN и сервер применят один пакет дважды.
 *
 * Ошибки хранилища и буфера здесь не пробрасываются: исключение из этого метода
 * рвёт `receiveLoop` транспорта и отменяет WebSocket, но пакет при этом уже
 * помечен обработанным — сервер получил бы повтор, а клиент его проигнорировал.
 * Запись в историю — best-effort: она не должна блокировать доставку в буфер.
 */
internal class DefaultClipboardIncomingSink(
    private val deduplicate: DeduplicateEventUseCase,
    private val resolve: ResolveIncomingClipboardUseCase,
    private val gateway: ClipboardGateway,
    private val historyWriter: ClipboardHistoryWriter,
    private val settings: ClipboardSettingsStore,
    private val clock: EpochClock,
    private val appSettings: AppSettingsStore? = null,
    private val notifier: IncomingClipNotifier? = null,
    private val deviceDirectory: DeviceDirectory? = null,
) : ClipboardIncomingSink {

    override suspend fun receive(packet: IncomingClipboardPacket): IncomingClipboardAction {
        // Пустой текст не приходит от корректного клиента (отправка пустого
        // запрещена), но битый/чужой пакет не должен оставлять пустую запись.
        if (packet.text.isBlank()) return IncomingClipboardAction.IGNORE

        // Лимит 512 КиБ из NFR проверяется и на приёме: отправитель мог быть
        // другой версии или злоумышленником, а буфер и история — наши.
        if (ClipboardLimits.utf8Size(packet.text) > ClipboardLimits.MAX_TEXT_BYTES) {
            return IncomingClipboardAction.IGNORE
        }

        if (deduplicate(packet.eventId)) return IncomingClipboardAction.IGNORE

        val nowMillis = clock.nowMillis()
        val current = settings.current()
        val action = resolve(
            isOnline = current.isOnline,
            autoInsert = current.autoInsert,
            nowMillis = nowMillis,
            lastLocalCaptureMillis = gateway.lastLocalCaptureMillis(),
        )
        runCatching {
            historyWriter.save(
                text = packet.text,
                timestampMillis = saneTimestamp(packet.copiedAtMillis, nowMillis),
                sourceDeviceId = packet.originDeviceId,
            )
        }
        if (action == IncomingClipboardAction.SAVE_AND_INSERT) {
            runCatching { gateway.insert(packet.text) }
        }
        notifyIncomingCopy(packet.originDeviceId)
        return action
    }

    /**
     * US-009: локальное уведомление о копии с другого устройства.
     *
     * Выключено по умолчанию, поэтому проверка настроек — до любых обращений к
     * платформе. Имя устройства берётся из кэша каталога; его отсутствие не
     * мешает показать уведомление без имени.
     */
    private suspend fun notifyIncomingCopy(originDeviceId: String) {
        val target = notifier ?: return
        val enabled = runCatching {
            appSettings?.current()?.notifications?.newCopies ?: false
        }.getOrDefault(false)
        if (!enabled) return
        val deviceName = runCatching { deviceDirectory?.deviceName(originDeviceId) }.getOrNull()
        runCatching { target.notifyIncoming(deviceName) }
    }

    /**
     * `timestamp` приходит от другого устройства и является недоверенным.
     *
     * Будущая метка (часы пира ушли вперёд) поднимала бы запись в начало истории
     * навсегда и выводила её из-под TTL-вытеснения. Неизвестное время (`0` и
     * меньше — протокол разрешает «время неизвестно») заменяем локальным.
     * Прошлое не трогаем: офлайн-очередь сервера живёт до 48 часов.
     */
    private fun saneTimestamp(timestampMillis: Long, nowMillis: Long): Long = when {
        timestampMillis <= 0L -> nowMillis
        timestampMillis > nowMillis + MAX_FUTURE_SKEW_MILLIS -> nowMillis
        else -> timestampMillis
    }

    private companion object {
        /** Допустимое расхождение часов между устройствами. */
        const val MAX_FUTURE_SKEW_MILLIS = 5L * 60L * 1000L
    }
}
