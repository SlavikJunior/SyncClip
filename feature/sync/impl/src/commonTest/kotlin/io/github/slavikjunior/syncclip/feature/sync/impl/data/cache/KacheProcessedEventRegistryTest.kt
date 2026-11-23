package io.github.slavikjunior.syncclip.feature.sync.impl.data.cache

import io.github.slavikjunior.syncclip.core.cache.KacheWrapper
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.DeduplicateEventUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

internal class KacheProcessedEventRegistryTest {

    private val clock = MutableClock()

    private fun registry(ttl: kotlin.time.Duration = 5.minutes) = KacheProcessedEventRegistry(
        cache = KacheWrapper(maxSize = 1_024, defaultTtl = ttl, clock = clock),
        ttl = ttl,
    )

    @Test
    fun `first delivery is new and repeat is duplicate`() = runTest {
        val useCase = DeduplicateEventUseCase(registry())

        assertFalse(useCase("event-1"), "первая доставка обрабатывается")
        assertTrue(useCase("event-1"), "повторная доставка — дубль")
    }

    @Test
    fun `different events are not duplicates`() = runTest {
        val useCase = DeduplicateEventUseCase(registry())

        assertFalse(useCase("event-1"))
        assertFalse(useCase("event-2"))
    }

    @Test
    fun `event is forgotten after ttl`() = runTest {
        val useCase = DeduplicateEventUseCase(registry(ttl = 5.minutes))

        useCase("event-1")
        clock.advance(5.minutes)
        assertTrue(useCase("event-1"), "на границе TTL запись ещё жива")

        clock.advance(1.milliseconds)
        assertFalse(useCase("event-1"), "после TTL событие снова считается новым")
    }

    @Test
    fun `parallel deliveries process the event once`() = runTest {
        val useCase = DeduplicateEventUseCase(registry())

        val results = (1..32)
            .map { async { useCase("event-race") } }
            .awaitAll()

        assertTrue(results.count { !it } == 1, "новым событие признано ровно один раз")
    }
}
