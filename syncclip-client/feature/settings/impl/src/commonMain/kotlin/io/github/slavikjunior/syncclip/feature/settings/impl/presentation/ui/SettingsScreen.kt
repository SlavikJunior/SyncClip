package io.github.slavikjunior.syncclip.feature.settings.impl.presentation.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeState
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.repository.DeviceRepository
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.usecase.ValidateDeviceNameUseCase
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state.DeviceEffect
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state.DeviceIntent
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state.DeviceManagementViewModel
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject

@Composable
internal fun SettingsScreen(
    theme: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository: DeviceRepository = koinInject()
    val viewModel = remember { DeviceManagementViewModel(repository, ValidateDeviceNameUseCase()) }
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.handleIntent(DeviceIntent.Load)
        viewModel.effect.collect { effect ->
            when (effect) {
                is DeviceEffect.ShowMessage -> snackbarHostState.showSnackbar(
                    message = getString(effect.message),
                )
            }
        }
    }

    SettingsContent(
        state = state,
        theme = theme,
        onThemeChange = onThemeChange,
        onIntent = viewModel::handleIntent,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}
