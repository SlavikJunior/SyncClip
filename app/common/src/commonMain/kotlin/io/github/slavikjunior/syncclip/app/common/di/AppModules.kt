package io.github.slavikjunior.syncclip.app.common.di

import io.github.slavikjunior.syncclip.core.di.databaseModule
import io.github.slavikjunior.syncclip.feature.history.impl.historyModule
import io.github.slavikjunior.syncclip.feature.settings.impl.settingsModule
import io.github.slavikjunior.syncclip.feature.sync.impl.syncModule
import org.koin.core.module.Module

/**
 * Модули DI клиента — единая точка сборки composition root (INV-01).
 *
 * [platformContext] нужен только Android-фабрике Room; на iOS и Desktop
 * передаётся `null`.
 */
public fun appModules(platformContext: Any? = null): List<Module> = listOf(
    databaseModule(platformContext),
    historyModule,
    settingsModule,
    syncModule,
)
