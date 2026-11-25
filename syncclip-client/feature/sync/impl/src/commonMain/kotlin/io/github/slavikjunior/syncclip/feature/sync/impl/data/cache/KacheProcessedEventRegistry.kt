package io.github.slavikjunior.syncclip.feature.sync.impl.data.cache

import io.github.slavikjunior.syncclip.core.cache.KeyValueCache
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ProcessedEventRegistry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

/**
 * Реестр обработанных `eventId` на in-memory кэше.
 *
 * Kache — общий кэш приложения, но здесь он используется как таблица
 * дедупликации: значение `true` означает «событие уже видели». TTL (5 минут по
 * PROFILE/FR-2.5) ограничивает память; повторная доставка после истечения
 * окна дедупликации уже не опасна, потому что запись в Room идемпотентна по id.
 *
 * Мьютекс нужен из-за двух каналов доставки: проверка и пометка должны быть
 * одной критической секцией.
 */
internal class KacheProcessedEventRegistry(
    private val cache: KeyValueCache<String, Boolean>,
    private val ttl: Duration,
) : ProcessedEventRegistry {

    private val mutex = Mutex()

    override suspend fun markProcessed(eventId: String): Boolean = mutex.withLock {
        if (cache.get(eventId) != null) return@withLock false
        cache.put(eventId, true, ttl)
        true
    }
}
