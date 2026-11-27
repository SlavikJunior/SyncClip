package io.github.slavikjunior.syncclip.app.common.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import syncclip.app.common.generated.resources.Res
import syncclip.app.common.generated.resources.tray_exit
import syncclip.app.common.generated.resources.tray_open_history
import syncclip.app.common.generated.resources.tray_pause
import syncclip.app.common.generated.resources.tray_resume
import syncclip.app.common.generated.resources.tray_status_offline
import syncclip.app.common.generated.resources.tray_status_online

/**
 * Локализованные подписи трея.
 *
 * Живут в `jvmMain` `:app:common`, потому что сгенерированный `Res` внутренний
 * для модуля: `:app:desktopApp` получает готовые строки, а не литералы.
 */
public data class TrayStrings(
    val openHistory: String,
    val pause: String,
    val resume: String,
    val exit: String,
    val statusOnline: String,
    val statusOffline: String,
)

@Composable
public fun TrayLabels(): TrayStrings = TrayStrings(
    openHistory = stringResource(Res.string.tray_open_history),
    pause = stringResource(Res.string.tray_pause),
    resume = stringResource(Res.string.tray_resume),
    exit = stringResource(Res.string.tray_exit),
    statusOnline = stringResource(Res.string.tray_status_online),
    statusOffline = stringResource(Res.string.tray_status_offline),
)
