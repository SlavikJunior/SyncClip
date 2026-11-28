package io.github.slavikjunior.syncclip.feature.settings.impl.presentation.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.slavikjunior.syncclip.core.designsystem.theme.FontScale
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeState
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state.DeviceEffect
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state.DeviceIntent
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state.DeviceManagementViewModel
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SettingsScreen(
    theme: ThemeState,
    fontScale: FontScale,
    onThemeChange: (ThemeState) -> Unit,
    onRegenerateRandomTheme: () -> Unit,
    onFontScaleChange: (FontScale) -> Unit,
    modifier: Modifier = Modifier,
) {
    // ViewModel, use case и настройки приходят из контейнера (INV-08, INV-22).
    val viewModel: DeviceManagementViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.handleIntent(DeviceIntent.Load)
        viewModel.effect.collect { effect ->
            when (effect) {
                is DeviceEffect.ShowMessage -> snackbarHostState.showSnackbar(
                    message = getString(effect.message, *effect.args.toTypedArray()),
                )
            }
        }
    }

    SettingsContent(
        state = state,
        theme = theme,
        fontScale = fontScale,
        onThemeChange = onThemeChange,
        onRegenerateRandomTheme = onRegenerateRandomTheme,
        onFontScaleChange = onFontScaleChange,
        onIntent = viewModel::handleIntent,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}
