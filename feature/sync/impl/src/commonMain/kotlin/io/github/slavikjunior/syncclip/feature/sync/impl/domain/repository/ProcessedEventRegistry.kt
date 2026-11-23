package io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository

/**
 * Реестр обработанных событий синхронизации (FR-2.5).
 *
 * Один и тот же пакет приходит двумя каналами — LAN и сервером, — поэтому
 * проверка и пометка обязаны быть атомарными: иначе оба канала успевают
 * пройти проверку до записи и пакет применяется дважды.
 */
internal interface ProcessedEventRegistry {

    /**
     * Проверяет [eventId] и помечает его обработанным за одну операцию.
     *
     * @return `true`, если событие встретилось впервые; `false` — дубль.
     */
    suspend fun markProcessed(eventId: String): Boolean
}
