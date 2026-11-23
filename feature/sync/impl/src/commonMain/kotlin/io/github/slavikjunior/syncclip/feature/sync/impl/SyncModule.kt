package io.github.slavikjunior.syncclip.feature.sync.impl

import io.github.slavikjunior.syncclip.core.cache.KacheWrapper
import io.github.slavikjunior.syncclip.core.cache.KeyValueCache
import io.github.slavikjunior.syncclip.feature.sync.impl.data.cache.KacheProcessedEventRegistry
import io.github.slavikjunior.syncclip.feature.sync.impl.data.repository.ClipboardSyncRepository
import io.github.slavikjunior.syncclip.feature.sync.impl.data.source.ClipboardSource
import io.github.slavikjunior.syncclip.feature.sync.impl.data.source.InMemoryClipboardSource
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardPacketSource
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ProcessedEventRegistry
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.DebounceClipboardUseCase
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.DeduplicateEventUseCase
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Duration.Companion.minutes

/** Окно дедупликации дублей доставки: LAN и сервер приходят почти одновременно. */
private val PROCESSED_EVENT_TTL = 5.minutes

/**
 * Лимит реестра: за 5 минут реальных событий заметно меньше, но запас
 * защищает от всплеска при первичной синхронизации.
 */
private const val PROCESSED_EVENT_CACHE_SIZE = 1_024

private const val PROCESSED_EVENT_CACHE = "processed-event-cache"

/** Публичный DI-контракт фичи. */
val syncModule = module {
    single<ClipboardSource> { InMemoryClipboardSource() }
    single { DebounceClipboardUseCase() }
    single<ClipboardPacketSource> { ClipboardSyncRepository(get(), get()) }
    single<KeyValueCache<String, Boolean>>(named(PROCESSED_EVENT_CACHE)) {
        KacheWrapper(maxSize = PROCESSED_EVENT_CACHE_SIZE, defaultTtl = PROCESSED_EVENT_TTL)
    }
    single<ProcessedEventRegistry> {
        KacheProcessedEventRegistry(get(named(PROCESSED_EVENT_CACHE)), PROCESSED_EVENT_TTL)
    }
    single { DeduplicateEventUseCase(get()) }
}
