package io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase

import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ProcessedEventRegistry

/**
 * FR-2.5: дубликаты определяются по `eventId`, TTL записи — 5 минут.
 *
 * Клиент игнорирует пакет, если он уже приходил любым каналом (LAN или сервер).
 */
internal class DeduplicateEventUseCase(
    private val registry: ProcessedEventRegistry,
) {

    /**
     * @return `true` — пакет уже обрабатывался и должен быть проигнорирован.
     */
    suspend operator fun invoke(eventId: String): Boolean = !registry.markProcessed(eventId)
}
