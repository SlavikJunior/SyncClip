package io.github.slavikjunior.syncclip.app.common.di

import io.github.slavikjunior.syncclip.core.di.clipboardModule
import io.github.slavikjunior.syncclip.core.di.databaseModule
import io.github.slavikjunior.syncclip.core.di.datastoreModule
import io.github.slavikjunior.syncclip.core.di.firebaseModule
import io.github.slavikjunior.syncclip.core.di.lanModule
import io.github.slavikjunior.syncclip.app.common.runtime.AppSectionRequests
import io.github.slavikjunior.syncclip.app.common.runtime.SyncRuntime
import io.github.slavikjunior.syncclip.core.di.networkModule
import io.github.slavikjunior.syncclip.core.di.platformServicesModule
import io.github.slavikjunior.syncclip.feature.auth.impl.authModule
import io.github.slavikjunior.syncclip.feature.history.impl.historyModule
import io.github.slavikjunior.syncclip.feature.settings.impl.settingsModule
import io.github.slavikjunior.syncclip.feature.sync.impl.syncModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Модули DI клиента — единая точка сборки composition root (INV-01).
 *
 * [platformContext] нужен Android-фабрикам Room и DataStore; на iOS и Desktop
 * передаётся `null`.
 */
public fun appModules(platformContext: Any? = null): List<Module> = listOf(
    clipboardModule(platformContext),
    lanModule(platformContext),
    databaseModule(platformContext),
    datastoreModule(platformContext),
    networkModule(),
    firebaseModule(),
    platformServicesModule(),
    authModule,
    historyModule,
    settingsModule,
    syncModule,
    runtimeModule,
)

/** Состояние приложения вне Compose: фоновый runtime и запросы навигации. */
private val runtimeModule: Module = module {
    single { AppSectionRequests() }
    single { SyncRuntime(sessionStore = get(), capture = get(), lan = get(), transport = get()) }
}
