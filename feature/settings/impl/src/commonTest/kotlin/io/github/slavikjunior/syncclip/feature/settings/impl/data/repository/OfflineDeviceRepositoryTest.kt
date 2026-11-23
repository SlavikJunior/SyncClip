package io.github.slavikjunior.syncclip.feature.settings.impl.data.repository

import io.github.slavikjunior.syncclip.core.cache.KacheWrapper
import io.github.slavikjunior.syncclip.core.database.PendingDeviceUpdateEntity
import io.github.slavikjunior.syncclip.feature.settings.impl.data.cache.DeviceCache
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceKind
import io.github.slavikjunior.syncclip.shared.model.Device
import io.github.slavikjunior.syncclip.shared.model.DeviceIcon
import io.github.slavikjunior.syncclip.shared.model.DevicePlatform
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

internal class OfflineDeviceRepositoryTest {

    private val clock = MutableClock()
    private val cache = DeviceCache(
        cache = KacheWrapper(maxSize = 1, defaultTtl = 5.minutes, clock = clock),
        clock = clock,
    )

    @Test
    fun `successful rename drops the cached directory`() = runTest {
        cache.store(listOf(device()))
        val repository = OfflineDeviceRepository(FakeDeviceApi(), FakePendingDeviceUpdateDao(), cache)

        repository.updateDevice("id-1", "new name", DeviceKind.LAPTOP)

        assertNull(cache.lastKnown(), "каталог больше не должен отдавать старое имя")
    }

    @Test
    fun `failed rename keeps the cached directory`() = runTest {
        cache.store(listOf(device()))
        val api = FakeDeviceApi().apply { respondWith(Result.failure(IllegalStateException("offline"))) }
        val repository = OfflineDeviceRepository(api, FakePendingDeviceUpdateDao(), cache)

        repository.updateDevice("id-1", "new name", DeviceKind.LAPTOP)

        assertNotNull(cache.lastKnown(), "без подтверждения сервера имя остаётся прежним")
    }

    @Test
    fun `flush with applied updates drops the cached directory`() = runTest {
        cache.store(listOf(device()))
        val pending = FakePendingDeviceUpdateDao(
            listOf(PendingDeviceUpdateEntity(deviceId = "id-1", name = "new name", icon = null)),
        )
        val repository = OfflineDeviceRepository(FakeDeviceApi(), pending, cache)

        repository.flushPending()

        assertNull(cache.lastKnown())
    }

    @Test
    fun `flush without pending updates keeps the cached directory`() = runTest {
        cache.store(listOf(device()))
        val repository = OfflineDeviceRepository(FakeDeviceApi(), FakePendingDeviceUpdateDao(), cache)

        repository.flushPending()

        assertNotNull(cache.lastKnown())
    }

    private fun device(): Device = Device(
        id = "id-1",
        name = "laptop",
        icon = DeviceIcon.DESKTOP,
        platform = DevicePlatform.ANDROID,
    )
}
