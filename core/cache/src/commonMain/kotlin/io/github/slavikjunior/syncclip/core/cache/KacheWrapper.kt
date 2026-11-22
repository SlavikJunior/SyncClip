package io.github.slavikjunior.syncclip.core.cache

import io.github.slavikjunior.kache.core.L1MemoryCache
import io.github.slavikjunior.kache.core.StorageRecord
import io.github.slavikjunior.kache.core.TimeSource
import kotlin.time.Duration

/**
 * In-memory реализация [KeyValueCache] на `L1MemoryCache` из библиотеки Kache.
 *
 * Всё, что знает про Kache, находится в этом модуле: фичи видят только
 * [KeyValueCache]. Модуль — инфраструктурный, поэтому кэш обязан жить в
 * application-scope контейнере (Koin `single`), а не в ViewModel.
 *
 * @param K Тип ключа.
 * @param V Тип значения.
 * @param maxSize Предельное число записей; должно быть положительным.
 * @param defaultTtl Срок жизни записи, если [put] вызван без явного `ttl`.
 *   `null` делает записи бессрочными.
 * @param clock Часы для TTL; в тестах подменяются на управляемые.
 */
public class KacheWrapper<K, V>(
    maxSize: Int,
    private val defaultTtl: Duration? = null,
    clock: Clock = Clock.System,
) : KeyValueCache<K, V> {

    private val delegate: L1MemoryCache<K, V> = L1MemoryCache(
        maxSize = maxSize,
        defaultTtl = defaultTtl,
        timeSource = TimeSource { clock.nowMillis() },
    )

    override suspend fun get(key: K): CacheEntry<V>? = delegate.get(key)?.toEntry()

    override suspend fun getStale(key: K): CacheEntry<V>? = delegate.getStale(key)?.toEntry()

    override suspend fun put(key: K, value: V, ttl: Duration?) {
        // null означает «взять defaultTtl кэша», а не «никогда не истекает»:
        // это семантика Kache, и подменять её снаружи нельзя.
        delegate.put(key = key, value = value, ttl = ttl ?: defaultTtl)
    }

    override suspend fun remove(key: K) {
        delegate.remove(key)
    }

    override suspend fun clear() {
        delegate.clear()
    }

    override suspend fun size(): Int = delegate.size()

    override suspend fun removeExpired(): Int = delegate.removeExpired()

    private fun StorageRecord<V>.toEntry(): CacheEntry<V> = CacheEntry(
        value = value,
        createdAtMillis = createdAt,
        ttl = ttl,
    )
}
