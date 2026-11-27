package io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository

/**
 * Запись копирования в локальную историю (FR-4.1).
 *
 * История живёт только на клиенте: сервер получает пакет, но не архив.
 * Ограничения по количеству/TTL/объёму применяет реализация.
 */
internal interface ClipboardHistoryWriter {

    /**
     * @param sourceDeviceId `null` — копирование на этом устройстве;
     *   иначе ID устройства-источника удалённого пакета.
     */
    suspend fun save(text: String, timestampMillis: Long, sourceDeviceId: String?)
}
