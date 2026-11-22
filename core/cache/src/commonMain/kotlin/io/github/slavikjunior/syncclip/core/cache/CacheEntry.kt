package io.github.slavikjunior.syncclip.core.cache

import kotlin.time.Duration

/**
 * Запись кэша: значение и его срок жизни.
 *
 * [ttl] == `null` означает «запись не истекает»; нулевой или отрицательный срок
 * делает её просроченной сразу.
 *
 * @param value Сохранённое значение.
 * @param createdAtMillis Момент записи в миллисекундах от эпохи.
 * @param ttl Срок жизни записи или `null`, если она бессрочная.
 */
public data class CacheEntry<out V>(
    public val value: V,
    public val createdAtMillis: Long,
    public val ttl: Duration? = null,
) {

    /**
     * Проверяет, истёк ли срок жизни записи.
     *
     * Время передаётся явно, чтобы решение не зависело от скрытого вызова часов.
     *
     * @param nowMillis Текущее время в миллисекундах от эпохи.
     */
    public fun isExpired(nowMillis: Long): Boolean {
        val lifetime = ttl ?: return false
        return nowMillis - createdAtMillis > lifetime.inWholeMilliseconds
    }
}
