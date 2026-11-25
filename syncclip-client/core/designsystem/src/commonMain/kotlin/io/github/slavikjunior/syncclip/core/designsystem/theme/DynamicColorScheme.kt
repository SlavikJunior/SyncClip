package io.github.slavikjunior.syncclip.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * Системная (Material You) палитра на Android 12+.
 *
 * На iOS/Desktop возвращает null — тема откатывается на Aqua/Winter.
 */
@Composable
internal expect fun PlatformDynamicColorScheme(darkTheme: Boolean): ColorScheme?
