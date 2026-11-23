package io.github.slavikjunior.syncclip.feature.settings.impl.data.repository

import io.github.slavikjunior.syncclip.core.cache.KacheWrapper
import io.github.slavikjunior.syncclip.feature.settings.impl.data.cache.DeviceCache
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceItem
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceKind
import io.github.slavikjunior.syncclip.shared.model.Device
import io.github.slavikjunior.syncclip.shared.model.DevicePlatform
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

internal class RepositoryDeviceDirectoryTest {

    private val clock = MutableClock()
    private val repository = FakeDeviceRepository()
    private val cache = DeviceCache(
        cache = KacheWrapper<String, List<Device>>(
            maxSize = 1,
            defaultTtl = 5.minutes,
            clock = clock,
        ),
        clock = clock,
    )
    private val directory = RepositoryDeviceDirectory(repository, cache)

    @Test
    fun `devices are loaded once within ttl`() = runTest {
        repository.respondWith(Result.success(listOf(device("laptop"))))

        assertEquals(listOf("laptop"), directory.devices().map { it.name })
        clock.advance(1.minutes)
        assertEquals(listOf("laptop"), directory.devices().map { it.name })

        assertEquals(1, repository.loadCalls)
    }

    @Test
    fun `expired snapshot is reloaded`() = runTest {
        repository.respondWith(Result.success(listOf(device("laptop"))))
        directory.devices()

        repository.respondWith(Result.success(listOf(device("phone"))))
        clock.advance(6.minutes)

        assertEquals(listOf("phone"), directory.devices().map { it.name })
        assertEquals(2, repository.loadCalls)
    }

    @Test
    fun `failed reload serves the last snapshot`() = runTest {
        repository.respondWith(Result.success(listOf(device("laptop"))))
        directory.devices()

        repository.respondWith(Result.failure(IllegalStateException("no network")))
        clock.advance(6.minutes)

        assertEquals(listOf("laptop"), directory.devices().map { it.name })
    }

    @Test
    fun `failed load without snapshot returns empty list`() = runTest {
        repository.respondWith(Result.failure(IllegalStateException("no network")))

        assertTrue(directory.devices().isEmpty())
    }

    @Test
    fun `invalidated snapshot is reloaded`() = runTest {
        repository.respondWith(Result.success(listOf(device("laptop"))))
        directory.devices()

        repository.respondWith(Result.success(listOf(device("tablet"))))
        cache.invalidate()

        assertEquals(listOf("tablet"), directory.devices().map { it.name })
        assertEquals(2, repository.loadCalls)
    }

    @Test
    fun `unknown platform does not break mapping`() = runTest {
        repository.respondWith(Result.success(listOf(device("laptop", platform = "PLAN9"))))

        assertEquals(DevicePlatform.UNKNOWN, directory.devices().single().platform)
    }

    private fun device(
        name: String,
        platform: String = DevicePlatform.ANDROID.name,
    ): DeviceItem = DeviceItem(
        id = "id-$name",
        name = name,
        icon = DeviceKind.LAPTOP,
        platform = platform,
        isCurrent = false,
    )
}
