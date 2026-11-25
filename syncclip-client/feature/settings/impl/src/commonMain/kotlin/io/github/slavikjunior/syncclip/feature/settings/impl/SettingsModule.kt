package io.github.slavikjunior.syncclip.feature.settings.impl

import io.github.slavikjunior.syncclip.core.cache.KacheWrapper
import io.github.slavikjunior.syncclip.core.cache.KeyValueCache
import io.github.slavikjunior.syncclip.core.database.PendingDeviceUpdateDao
import io.github.slavikjunior.syncclip.feature.settings.impl.data.cache.DeviceCache
import io.github.slavikjunior.syncclip.feature.settings.impl.data.remote.DeviceApi
import io.github.slavikjunior.syncclip.feature.settings.impl.data.remote.KtorDeviceApi
import io.github.slavikjunior.syncclip.feature.settings.impl.data.repository.OfflineDeviceRepository
import io.github.slavikjunior.syncclip.feature.settings.impl.data.repository.RepositoryDeviceDirectory
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.repository.DeviceRepository
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.usecase.ValidateDeviceNameUseCase
import io.github.slavikjunior.syncclip.shared.model.Device
import io.github.slavikjunior.syncclip.shared.model.DeviceDirectory
import io.github.slavikjunior.syncclip.shared.network.SyncClipHttpClient
import io.ktor.client.HttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Duration.Companion.minutes

/** Каталог устройств меняется редко, но должен подхватывать переименование. */
private val DEVICE_DIRECTORY_TTL = 5.minutes

private const val DEVICE_DIRECTORY_CACHE = "device-directory-cache"

/** Публичный DI-контракт фичи. */
val settingsModule = module {
    single<SyncClipHttpClient> {
        SyncClipHttpClient(
            baseUrl = getOrNull<String>(named("apiBaseUrl")) ?: "http://localhost:8080",
            tokenProvider = { getOrNull<String>(named("authToken")).orEmpty() },
        )
    }
    single<HttpClient> { get<SyncClipHttpClient>().client }
    single<DeviceApi> { KtorDeviceApi(get()) }
    // Каталог устройств нужен каждому экрану истории и синхронизации, поэтому
    // кэш живёт в application-scope, а не в ViewModel.
    single<KeyValueCache<String, List<Device>>>(named(DEVICE_DIRECTORY_CACHE)) {
        KacheWrapper(maxSize = 1, defaultTtl = DEVICE_DIRECTORY_TTL)
    }
    single { DeviceCache(get(named(DEVICE_DIRECTORY_CACHE))) }
    single<DeviceRepository> { OfflineDeviceRepository(get(), get<PendingDeviceUpdateDao>(), get()) }
    single<DeviceDirectory> { RepositoryDeviceDirectory(get(), get()) }
    single { ValidateDeviceNameUseCase() }
}
