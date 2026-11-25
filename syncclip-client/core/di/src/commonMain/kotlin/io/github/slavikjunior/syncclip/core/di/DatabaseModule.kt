package io.github.slavikjunior.syncclip.core.di

import io.github.slavikjunior.syncclip.core.database.AppDatabase
import io.github.slavikjunior.syncclip.core.database.ClipboardEventDao
import io.github.slavikjunior.syncclip.core.database.PendingDeviceUpdateDao
import io.github.slavikjunior.syncclip.core.database.clipboardDatabaseFactory
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Общий провайдер БД для всех фич.
 *
 * [platformContext] — Android `Context` для фабрики Room; на iOS/Desktop
 * передаётся `null`. Точка входа приложения — единственное место, где он известен,
 * поэтому модуль создаётся функцией, а не глобальным `val`.
 */
public fun databaseModule(platformContext: Any? = null): Module = module {
    single<AppDatabase> {
        clipboardDatabaseFactory().create(name = "syncclip.db", context = platformContext)
    }
    single<ClipboardEventDao> { get<AppDatabase>().clipboardEventDao() }
    single<PendingDeviceUpdateDao> { get<AppDatabase>().pendingDeviceUpdateDao() }
}
