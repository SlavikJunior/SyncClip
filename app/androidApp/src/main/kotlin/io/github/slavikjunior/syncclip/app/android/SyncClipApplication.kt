package io.github.slavikjunior.syncclip.app.android

import android.app.Application
import io.github.slavikjunior.syncclip.app.common.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Composition root Android-приложения: Koin поднимается до первого экрана,
 * `Context` передаётся в фабрику Room.
 */
class SyncClipApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SyncClipApplication)
            modules(appModules(platformContext = this@SyncClipApplication))
        }
    }
}
