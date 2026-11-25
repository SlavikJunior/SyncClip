package io.github.slavikjunior.syncclip.feature.sync.impl

import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.DeduplicateEventUseCase
import org.koin.dsl.koinApplication
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Проверяет wiring, который не виден компилятору: реестр получает кэш
 * `KeyValueCache<String, Boolean>` из DI, а не создаёт его сам.
 */
internal class SyncModuleTest {

    private val koin = koinApplication { modules(syncModule) }.koin

    @AfterTest
    fun tearDown() {
        koin.close()
    }

    @Test
    fun `deduplication use case is resolvable`() {
        assertNotNull(koin.get<DeduplicateEventUseCase>())
    }
}
