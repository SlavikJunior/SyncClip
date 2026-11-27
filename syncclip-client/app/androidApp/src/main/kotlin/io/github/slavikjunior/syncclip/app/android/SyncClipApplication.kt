package io.github.slavikjunior.syncclip.app.android

import android.app.Application
import io.github.slavikjunior.syncclip.app.android.push.SyncClipNotifications
import io.github.slavikjunior.syncclip.app.common.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Composition root Android-приложения: Koin поднимается до первого экрана,
 * `Context` передаётся в фабрики Room и DataStore.
 *
 * Firebase инициализируется сам (`FirebaseInitProvider` из google-services.json);
 * здесь создаётся только канал уведомлений — FCM требует его до первого push.
 */
class SyncClipApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        SyncClipNotifications.ensureChannel(this)
        startKoin {
            androidContext(this@SyncClipApplication)
            modules(appModules(platformContext = this@SyncClipApplication))
        }
    }
}
