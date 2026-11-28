package io.github.slavikjunior.syncclip.app.ios

import androidx.compose.ui.window.ComposeUIViewController
import io.github.slavikjunior.syncclip.app.common.di.appModules
import io.github.slavikjunior.syncclip.app.common.ui.SyncClipApp
import io.github.slavikjunior.syncclip.app.ios.documents.IosDocumentGateway
import io.github.slavikjunior.syncclip.app.ios.security.IosBiometricAuthenticator
import io.github.slavikjunior.syncclip.core.platform.BiometricAuthenticator
import io.github.slavikjunior.syncclip.core.platform.DocumentGateway
import org.koin.core.context.startKoin
import org.koin.dsl.module
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
 * цикл приложения. Платформенные реализации Phase 2 регистрируются здесь:
 * им нужны UIKit-API, недоступные общему модулю.
 */
fun startSyncClipKoin() {
    startKoin {
        modules(
            appModules() + module {
                single<DocumentGateway> { IosDocumentGateway() }
                single<BiometricAuthenticator> { IosBiometricAuthenticator() }
            },
        )
    }
}
