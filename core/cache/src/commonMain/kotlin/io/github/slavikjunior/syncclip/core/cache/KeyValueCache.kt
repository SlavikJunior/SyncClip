package io.github.slavikjunior.syncclip.core.cache

import kotlin.time.Duration

/**
 * Контракт in-memory кэша «ключ → значение» с TTL и вытеснением.
 *
 * Фичи зависят от этого интерфейса, а не от библиотеки Kache: реализация
 * ([KacheWrapper]) остаётся инфраструктурной деталью `:core:cache`.
 * Контракт намеренно не содержит ни `Flow`, ни стратегий кэширования:
 * состояния экрана собирает MVI-слой фичи (INV-08), а кэш отвечает только
 * за хранение.
 */
public interface KeyValueCache<K, V> {

    /**
     * Возвращает запись для [key], если она есть и не истекла.
     *
     * Просроченная запись при этом удаляется, поэтому `null` означает
     * «свежих данных нет», а не «есть только устаревшие». Для второго случая
     * есть [getStale].
     */
    public suspend fun get(key: K): CacheEntry<V>?

    /** Возвращает запись для [key] независимо от срока жизни, если она есть. */
    public suspend fun getStale(key: K): CacheEntry<V>?

    /**
     * Сохраняет [value] под ключом [key].
     *
     * @param ttl Срок жизни или `null` — использовать `defaultTtl` кэша.
     *   Бессрочная запись возможна только в кэше без `defaultTtl`.
     */
    public suspend fun put(key: K, value: V, ttl: Duration? = null)

    /** Удаляет запись [key], если она есть. */
    public suspend fun remove(key: K)

    /** Удаляет все записи. */
    public suspend fun clear()

    /** Число записей, включая просроченные и ещё не вытесненные. */
    public suspend fun size(): Int

    /** Удаляет просроченные записи. Возвращает число удалённых. */
    public suspend fun removeExpired(): Int
}
