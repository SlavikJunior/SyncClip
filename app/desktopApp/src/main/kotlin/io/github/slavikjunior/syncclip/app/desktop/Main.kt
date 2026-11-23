package io.github.slavikjunior.syncclip.app.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.slavikjunior.syncclip.app.common.di.appModules
import io.github.slavikjunior.syncclip.app.common.ui.SyncClipApp
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(appModules())
    }

    application {
        val windowState = rememberWindowState(size = DpSize(1200.dp, 800.dp))
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "SyncClip",
        ) {
            SyncClipApp()
        }
    }
}
