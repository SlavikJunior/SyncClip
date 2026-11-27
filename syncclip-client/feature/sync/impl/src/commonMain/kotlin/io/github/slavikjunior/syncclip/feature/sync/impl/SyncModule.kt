package io.github.slavikjunior.syncclip.feature.sync.impl

import io.github.slavikjunior.syncclip.core.cache.KacheWrapper
import io.github.slavikjunior.syncclip.core.cache.KeyValueCache
import io.github.slavikjunior.syncclip.core.clipboard.EpochClock
import io.github.slavikjunior.syncclip.core.datastore.DeviceIdentityStore
import io.github.slavikjunior.syncclip.core.datastore.SessionStore
import io.github.slavikjunior.syncclip.feature.sync.api.ClipboardCaptureStarter
import io.github.slavikjunior.syncclip.feature.sync.api.E2eeKeyProvisioner
import io.github.slavikjunior.syncclip.feature.sync.api.LanTransportStarter
import io.github.slavikjunior.syncclip.feature.sync.api.SyncTransportStarter
import io.github.slavikjunior.syncclip.feature.sync.impl.data.remote.KtorE2eeKeyProvisioner
import io.github.slavikjunior.syncclip.feature.sync.impl.data.cache.KacheProcessedEventRegistry
import io.github.slavikjunior.syncclip.feature.sync.impl.data.remote.KtorSyncSocketFactory
import io.github.slavikjunior.syncclip.feature.sync.impl.data.remote.SyncSocketFactory
import io.github.slavikjunior.syncclip.feature.sync.impl.data.repository.ClipboardCaptureRepository
import io.github.slavikjunior.syncclip.feature.sync.impl.data.repository.DefaultClipboardIncomingSink
import io.github.slavikjunior.syncclip.feature.sync.impl.data.repository.RoomClipboardHistoryWriter
import io.github.slavikjunior.syncclip.feature.sync.impl.data.transport.IncomingPacketProcessor
import io.github.slavikjunior.syncclip.feature.sync.impl.data.transport.LanSyncTransport
import io.github.slavikjunior.syncclip.feature.sync.impl.data.transport.SyncTransport
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardCaptureFeed
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardHistoryWriter
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardIncomingSink
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardPacketSource
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ProcessedEventRegistry
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ServerPacketSource
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.AcceptLocalCaptureUseCase
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.DebounceClipboardUseCase
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.DeduplicateEventUseCase
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.ResolveIncomingClipboardUseCase
import io.github.slavikjunior.syncclip.feature.sync.impl.presentation.state.SyncStatusViewModel
import io.github.slavikjunior.syncclip.shared.crypto.AesGcmPayloadCipher
import io.github.slavikjunior.syncclip.shared.crypto.E2eeKeyProvider
import io.github.slavikjunior.syncclip.shared.crypto.PayloadCipher
import io.github.slavikjunior.syncclip.shared.model.DeviceDirectory
import io.github.slavikjunior.syncclip.shared.network.SyncClipHttpClient
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

/**
 * Публичный DI-контракт фичи.
 *
 * Конвейер захвата — `single`: системный буфер должен обслуживать один
 * владелец на приложение, иначе дедупликация считается по подписчику.
 */
val syncModule = module {
    single { DebounceClipboardUseCase() }
    single { AcceptLocalCaptureUseCase() }
    single { ResolveIncomingClipboardUseCase() }
    single<ClipboardHistoryWriter> {
        RoomClipboardHistoryWriter(dao = get(), clock = EpochClock.System, settings = get())
    }
    single {
        ClipboardCaptureRepository(
            gateway = get(),
            pendingShares = get(),
            historyWriter = get(),
            settings = get(),
            accept = get(),
            debounce = get(),
            clock = EpochClock.System,
        )
    }
    single<ClipboardPacketSource> { get<ClipboardCaptureRepository>() }
    single<ClipboardCaptureFeed> { get<ClipboardCaptureRepository>() }
    single<ClipboardCaptureStarter> { get<ClipboardCaptureRepository>() }
    single<ClipboardIncomingSink> {
        DefaultClipboardIncomingSink(
            deduplicate = get(),
            resolve = get(),
            gateway = get(),
            historyWriter = get(),
            settings = get(),
            clock = EpochClock.System,
        )
    }
    single<PayloadCipher> { AesGcmPayloadCipher() }
    single<SyncSocketFactory> {
        KtorSyncSocketFactory(
            http = get<SyncClipHttpClient>(),
            deviceId = { get<DeviceIdentityStore>().identity().deviceId },
        )
    }
    single { IncomingPacketProcessor(cipher = get(), sink = get()) }
    single {
        LanSyncTransport(
            server = get(),
            discoveryFactory = get(),
            dialer = get(),
            keyProvider = get(),
            identity = get(),
            session = get<SessionStore>(),
            outgoing = get(),
            processor = get(),
            settings = get(),
            cipher = get(),
            clock = EpochClock.System,
        )
    }
    single<ServerPacketSource> { get<LanSyncTransport>() }
    single<LanTransportStarter> { get<LanSyncTransport>() }
    single {
        SyncTransport(
            socketFactory = get(),
            keyProvider = get<E2eeKeyProvider>(),
            identity = get(),
            serverPackets = get(),
            incomingSink = get(),
            settings = get(),
            cipher = get(),
            clock = EpochClock.System,
            sessionRefresher = getOrNull(),
        )
    }
    single<SyncTransportStarter> { get<SyncTransport>() }
    single<E2eeKeyProvisioner> {
        KtorE2eeKeyProvisioner(
            client = get(),
            session = get(),
            cipher = get(),
            lockout = getOrNull(),
        )
    }
    single<KeyValueCache<String, Boolean>>(named(PROCESSED_EVENT_CACHE)) {
        KacheWrapper(maxSize = PROCESSED_EVENT_CACHE_SIZE, defaultTtl = PROCESSED_EVENT_TTL)
    }
    single<ProcessedEventRegistry> {
        KacheProcessedEventRegistry(get(named(PROCESSED_EVENT_CACHE)), PROCESSED_EVENT_TTL)
    }
    single { DeduplicateEventUseCase(get()) }
    factory {
        SyncStatusViewModel(
            captures = get(),
            packets = get(),
            settings = get(),
            starter = get(),
            deviceDirectory = getOrNull<DeviceDirectory>(),
        )
    }
}
