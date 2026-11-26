package io.github.slavikjunior.syncclip.core.di

import io.github.slavikjunior.syncclip.core.clipboard.ClipboardGateway
import io.github.slavikjunior.syncclip.core.clipboard.PendingShareSource
import io.github.slavikjunior.syncclip.core.clipboard.SystemClipboard
import io.github.slavikjunior.syncclip.core.clipboard.createClipboardGateway
import io.github.slavikjunior.syncclip.core.clipboard.createPendingShareSource
import io.github.slavikjunior.syncclip.core.clipboard.createSystemClipboard
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Системный буфер обмена и gateway с правилами паспорта.
 *
 * [platformContext] — Android `Context`; на iOS/Desktop передаётся `null`.
 * Gateway живёт в application-scope: дедупликация и защита от эха должны
 * считать события один раз на приложение, а не на экран.
 */
public fun clipboardModule(platformContext: Any? = null): Module = module {
    single<SystemClipboard> { createSystemClipboard(platformContext) }
    single<ClipboardGateway> { createClipboardGateway(get()) }
    single<PendingShareSource> { createPendingShareSource(platformContext) }
}
