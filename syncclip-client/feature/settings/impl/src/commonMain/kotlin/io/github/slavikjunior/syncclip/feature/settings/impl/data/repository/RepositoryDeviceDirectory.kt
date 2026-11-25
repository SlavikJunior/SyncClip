package io.github.slavikjunior.syncclip.feature.settings.impl.data.repository

import io.github.slavikjunior.syncclip.feature.settings.impl.data.cache.DeviceCache
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceItem
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.repository.DeviceRepository
import io.github.slavikjunior.syncclip.shared.model.Device
import io.github.slavikjunior.syncclip.shared.model.DeviceDirectory
import io.github.slavikjunior.syncclip.shared.model.DeviceIcon
import io.github.slavikjunior.syncclip.shared.model.DevicePlatform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Адаптер настроек к общему каталогу устройств.
 *
 * Свежий снимок отдаётся из кэша (Kache, TTL в DI), поэтому History не дергает
 * сеть на каждую запись. При ошибке загрузки возвращается прошлый снимок, если
 * он есть, иначе пустой список — экран из-за каталога не падает.
 */
internal class RepositoryDeviceDirectory(
    private val repository: DeviceRepository,
    private val cache: DeviceCache,
) : DeviceDirectory {

    /** Сливает одновременные загрузки: два экрана не должны тянуть сеть дважды. */
    private val loadMutex = Mutex()

    override suspend fun devices(): List<Device> {
        cache.fresh()?.let { return it }

        return loadMutex.withLock {
            // Пока ждали мьютекс, соседний вызов мог уже заполнить кэш.
            cache.fresh()?.let { return@withLock it }

            repository.loadDevices().fold(
                onSuccess = { items ->
                    items.map { it.toShared() }.also { loaded -> cache.store(loaded) }
                },
                onFailure = { cache.lastKnown().orEmpty() },
            )
        }
    }
}

internal fun DeviceItem.toShared(): Device = Device(
    id = id,
    name = name,
    icon = runCatching { DeviceIcon.valueOf(icon.name) }.getOrDefault(DeviceIcon.DESKTOP),
    platform = runCatching { DevicePlatform.valueOf(platform) }.getOrDefault(DevicePlatform.UNKNOWN),
    isCurrent = isCurrent,
)
