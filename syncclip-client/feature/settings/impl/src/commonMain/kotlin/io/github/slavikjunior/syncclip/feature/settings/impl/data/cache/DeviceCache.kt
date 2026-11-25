package io.github.slavikjunior.syncclip.feature.settings.impl.data.cache

import io.github.slavikjunior.syncclip.core.cache.CacheEntry
import io.github.slavikjunior.syncclip.core.cache.Clock
import io.github.slavikjunior.syncclip.core.cache.KeyValueCache
import io.github.slavikjunior.syncclip.shared.model.Device

/**
 * Типизированный фасад над кэшем каталога устройств.
 *
 * Каталог нужен History и Sync на каждую отрисовку, а его загрузка — сетевой
 * запрос. Кэш живёт в application-scope (Koin `single`), поэтому переживает
 * экран; ключ и TTL скрыты здесь, чтобы снаружи не было «магических» строк.
 *
 * Свежесть проверяется по `CacheEntry`, а не через `get`: `get` удаляет
 * просроченную запись, и тогда при ошибке сети отдать последний снимок было бы
 * нечем.
 */
internal class DeviceCache(
    private val cache: KeyValueCache<String, List<Device>>,
    private val clock: Clock = Clock.System,
) {

    /** Свежий снимок каталога или `null`, если он истёк либо не заполнен. */
    suspend fun fresh(): List<Device>? = snapshot()
        ?.takeUnless { entry -> entry.isExpired(clock.nowMillis()) }
        ?.value

    /** Последний снимок каталога независимо от срока жизни. */
    suspend fun lastKnown(): List<Device>? = snapshot()?.value

    suspend fun store(devices: List<Device>) {
        cache.put(KEY, devices)
    }

    /** Сбрасывает снимок после изменения устройств на сервере. */
    suspend fun invalidate() {
        cache.remove(KEY)
    }

    private suspend fun snapshot(): CacheEntry<List<Device>>? = cache.getStale(KEY)

    private companion object {
        const val KEY = "device-directory"
    }
}
