package io.github.slavikjunior.syncclip.feature.history.impl.data.cache

import io.github.slavikjunior.syncclip.core.cache.KeyValueCache
import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntry

/**
 * Кэш первой страницы истории (NFR: открытие истории < 50 мс).
 *
 * Кэшируется только первый экран без поискового запроса:
 * - ключ не содержит пользовательских данных — поисковая строка остаётся в Room;
 * - память ограничена сверху одним экраном, а не всей историей;
 * - Room остаётся источником правды: поток обновляет кэш, а любая мутация его
 *   сбрасывает, поэтому устаревшая запись живёт не дольше одного TTL.
 */
internal class HistoryPageCache(
    private val cache: KeyValueCache<String, List<HistoryEntry>>,
) {

    /** Первая страница без поиска или `null`, если кэш пуст либо истёк. */
    suspend fun firstPage(limit: Int): List<HistoryEntry>? = cache.get(key(limit))?.value

    /**
     * Запоминает страницу, если это первый экран без поиска и он не слишком
     * велик: тексты из буфера могут достигать 512 КБ каждый.
     */
    suspend fun putFirstPage(limit: Int, page: List<HistoryEntry>) {
        if (page.isEmpty()) return
        if (page.sumOf { entry -> entry.text.length } > MAX_CACHED_CHARS) return
        cache.put(key(limit), page)
    }

    /** Сбрасывает снимок после удаления, закрепления или восстановления записи. */
    suspend fun invalidate() {
        cache.clear()
    }

    private fun key(limit: Int): String = "$KEY_PREFIX$limit"

    private companion object {
        const val KEY_PREFIX = "history-first-page:"

        /** ~128 КБ на страницу: два-три экрана в памяти не угрожают лимиту приложения. */
        const val MAX_CACHED_CHARS = 64_000
    }
}
