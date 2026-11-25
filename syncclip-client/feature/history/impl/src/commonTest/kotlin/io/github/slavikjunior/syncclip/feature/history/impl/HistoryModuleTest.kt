package io.github.slavikjunior.syncclip.feature.history.impl

import io.github.slavikjunior.syncclip.feature.history.impl.data.cache.HistoryPageCache
import org.koin.dsl.koinApplication
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Проверяет wiring, который не виден компилятору: кэш зарегистрирован как
 * `KeyValueCache<String, List<HistoryEntry>>`, и Koin находит его по параметру
 * конструктора [HistoryPageCache].
 */
internal class HistoryModuleTest {

    private val koin = koinApplication { modules(historyModule) }.koin

    @AfterTest
    fun tearDown() {
        koin.close()
    }

    @Test
    fun `history page cache is resolvable`() {
        assertNotNull(koin.get<HistoryPageCache>())
    }
}
