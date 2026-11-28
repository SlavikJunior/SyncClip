package io.github.slavikjunior.syncclip.app.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.slavikjunior.syncclip.app.common.di.appModules
import io.github.slavikjunior.syncclip.app.common.runtime.AppSectionRequests
import io.github.slavikjunior.syncclip.app.common.runtime.SyncRuntime
import io.github.slavikjunior.syncclip.app.common.ui.SyncClipApp
import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettings
import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.Dimension
import io.github.slavikjunior.syncclip.app.common.ui.TrayLabels
import io.github.slavikjunior.syncclip.app.desktop.documents.DesktopDocumentGateway
import io.github.slavikjunior.syncclip.app.desktop.theme.SyncClipTrayIcon
import io.github.slavikjunior.syncclip.core.platform.DocumentGateway
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * Точка входа Desktop: окно + системный трей + глобальный хоткей.
 *
 * Конвейер запускается в области жизни процесса, а не композиции: закрытие окна
 * сворачивает приложение в трей, захват и синхронизация продолжают работать.
 */
fun main() {
    val koin = startKoin {
        modules(appModules())
    }.koin
    // US-018: на Desktop системный диалог — Swing JFileChooser.
    loadKoinModules(
        module {
            single<DocumentGateway> { DesktopDocumentGateway() }
        },
    )

    val runtime: SyncRuntime = koin.get()
    val sectionRequests: AppSectionRequests = koin.get()
    val settings: ClipboardSettingsStore = koin.get()
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    runtime.start(appScope)

    val windowVisible = mutableStateOf(true)
    val openHistory: () -> Unit = {
        sectionRequests.openHistory()
        windowVisible.value = true
    }

    val hotkey = GlobalHotkey(onTrigger = openHistory)
    hotkey.start()

    application {
        val visible by windowVisible
        var clipboardSettings by remember { mutableStateOf(ClipboardSettings()) }
        val scope = rememberCoroutineScope()
        val labels = TrayLabels()

        LaunchedEffect(settings) {
            settings.state.collect { clipboardSettings = it }
        }

        Tray(
            icon = SyncClipTrayIcon,
            tooltip = if (clipboardSettings.isOnline) {
                labels.statusOnline
            } else {
                labels.statusOffline
            },
            onAction = openHistory,
        ) {
            Item(labels.openHistory, onClick = openHistory)
            Item(
                if (clipboardSettings.isOnline) labels.pause else labels.resume,
                onClick = {
                    // Сбой записи в DataStore не должен ронять трей-меню:
                    // пункт просто останется в прежнем состоянии.
                    scope.launch {
                        runCatching { settings.setOnline(!clipboardSettings.isOnline) }
                    }
                },
            )
            Separator()
            Item(
                labels.exit,
                onClick = {
                    hotkey.stop()
                    appScope.cancel()
                    koin.close()
                    exitApplication()
                },
            )
        }

        val windowState = rememberWindowState(size = DpSize(1200.dp, 800.dp))
        Window(
            visible = visible,
            onCloseRequest = { windowVisible.value = false },
            state = windowState,
            title = "SyncClip",
        ) {
            // US-003: минимальный размер окна 800×600 — ниже него ломается
            // адаптивная раскладка (rail + контент).
            LaunchedEffect(window) {
                window.minimumSize = Dimension(MIN_WINDOW_WIDTH_PX, MIN_WINDOW_HEIGHT_PX)
            }
            SyncClipApp()
        }
    }
}

/** Минимальный размер окна Desktop (US-003): 800×600 dp при плотности 1.0. */
private const val MIN_WINDOW_WIDTH_PX = 800
private const val MIN_WINDOW_HEIGHT_PX = 600
