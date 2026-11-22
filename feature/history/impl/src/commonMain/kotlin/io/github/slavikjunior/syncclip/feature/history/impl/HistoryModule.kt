package io.github.slavikjunior.syncclip.feature.history.impl

import io.github.slavikjunior.syncclip.core.cache.KacheWrapper
import io.github.slavikjunior.syncclip.core.cache.KeyValueCache
import io.github.slavikjunior.syncclip.core.database.ClipboardEventDao
import io.github.slavikjunior.syncclip.feature.history.impl.data.cache.HistoryPageCache
import io.github.slavikjunior.syncclip.feature.history.impl.data.repository.RoomHistoryRepository
import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntry
import io.github.slavikjunior.syncclip.feature.history.impl.domain.repository.HistoryRepository
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryViewModel
import io.github.slavikjunior.syncclip.shared.model.DeviceDirectory
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Duration.Companion.minutes

/**
 * Снимок живёт недолго: источник правды — Room, а кэш закрывает лишь окно до
 * первого ответа DAO после возврата на экран.
 */
private val HISTORY_PAGE_TTL = 1.minutes

/** Два экрана (разные limit) — больше не нужно. */
private const val HISTORY_PAGE_CACHE_SIZE = 2

private const val HISTORY_PAGE_CACHE = "history-page-cache"

/** Публичный контракт DI фичи: остальные объявления — internal (INV-26). */
val historyModule = module {
    single<KeyValueCache<String, List<HistoryEntry>>>(named(HISTORY_PAGE_CACHE)) {
        KacheWrapper(maxSize = HISTORY_PAGE_CACHE_SIZE, defaultTtl = HISTORY_PAGE_TTL)
    }
    single { HistoryPageCache(get(named(HISTORY_PAGE_CACHE))) }
    single<HistoryRepository> { RoomHistoryRepository(get<ClipboardEventDao>(), get()) }
    factory { HistoryViewModel(get(), getOrNull<DeviceDirectory>()) }
}
