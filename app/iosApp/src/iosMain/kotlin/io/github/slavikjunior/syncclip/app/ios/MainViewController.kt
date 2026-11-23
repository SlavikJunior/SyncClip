package io.github.slavikjunior.syncclip.app.ios

import androidx.compose.ui.window.ComposeUIViewController
import io.github.slavikjunior.syncclip.app.common.di.appModules
import io.github.slavikjunior.syncclip.app.common.ui.SyncClipApp
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

/**
 * Точка входа Compose Multiplatform из SwiftUI.
 * В Swift доступна как `MainViewControllerKt.MainViewController()`.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    SyncClipApp()
}

/**
 * Запуск Koin из Swift `App.init()`. Вызывается ровно один раз за жизненный
 * цикл приложения.
 */
fun startSyncClipKoin() {
    startKoin {
        modules(appModules())
    }
}
