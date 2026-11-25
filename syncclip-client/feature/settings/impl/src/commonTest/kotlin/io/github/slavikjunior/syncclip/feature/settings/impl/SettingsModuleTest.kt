package io.github.slavikjunior.syncclip.feature.settings.impl

import io.github.slavikjunior.syncclip.feature.settings.impl.data.cache.DeviceCache
import org.koin.dsl.koinApplication
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Проверяет wiring, который не виден компилятору: Koin ищет зависимости по
 * полному типу, поэтому кэш зарегистрирован как `KeyValueCache`, а не как
 * конкретный `KacheWrapper`.
 */
internal class SettingsModuleTest {

    private val koin = koinApplication { modules(settingsModule) }.koin

    @AfterTest
    fun tearDown() {
        koin.close()
    }

    @Test
    fun `device cache is resolvable`() {
        assertNotNull(koin.get<DeviceCache>())
    }
}
