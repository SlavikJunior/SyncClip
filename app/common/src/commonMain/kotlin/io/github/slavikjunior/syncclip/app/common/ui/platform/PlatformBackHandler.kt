package io.github.slavikjunior.syncclip.app.common.ui.platform

import androidx.compose.runtime.Composable

/**
 * Системная кнопка/жест «назад». На Android делегируется
 * `androidx.activity.compose.BackHandler`, на остальных платформах —
 * no-op: выход из детального экрана доступен по стрелке в TopAppBar.
 */
@Composable
internal expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
