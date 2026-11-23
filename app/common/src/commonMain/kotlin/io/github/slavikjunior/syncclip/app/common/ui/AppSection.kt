package io.github.slavikjunior.syncclip.app.common.ui

import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIconKind
import org.jetbrains.compose.resources.StringResource
import syncclip.app.common.generated.resources.Res
import syncclip.app.common.generated.resources.nav_history
import syncclip.app.common.generated.resources.nav_settings
import syncclip.app.common.generated.resources.nav_sync

/** Верхнеуровневые разделы приложения. */
internal enum class AppSection(
    val label: StringResource,
    val icon: SyncClipIconKind,
) {
    HISTORY(Res.string.nav_history, SyncClipIconKind.HISTORY),
    SYNC(Res.string.nav_sync, SyncClipIconKind.SYNC),
    SETTINGS(Res.string.nav_settings, SyncClipIconKind.SETTINGS),
}
