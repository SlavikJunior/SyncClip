package io.github.slavikjunior.syncclip.app.android

import android.app.Application
import io.github.slavikjunior.syncclip.app.android.push.AndroidIncomingClipNotifier
import io.github.slavikjunior.syncclip.app.android.push.SyncClipNotifications
import io.github.slavikjunior.syncclip.app.common.di.appModules
import io.github.slavikjunior.syncclip.core.platform.IncomingClipNotifier
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

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
            modules(
                appModules(platformContext = this@SyncClipApplication) + module {
                    // US-009: уведомление о входящей копии показывает платформа.
                    single<IncomingClipNotifier> {
                        AndroidIncomingClipNotifier(get(), get())
                    }
                },
            )
        }
    }
}
