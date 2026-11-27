package io.github.slavikjunior.syncclip.feature.auth.impl.presentation.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.slavikjunior.syncclip.feature.auth.impl.presentation.state.AuthEffect
import io.github.slavikjunior.syncclip.feature.auth.impl.presentation.state.AuthViewModel
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

/**
 * Экран входа: единственная точка, где создаётся ViewModel (INV-26).
 *
 * [onAuthenticated] вызывается после сохранения сессии — composition root
 * переключает приложение на основной экран.
 */
@Composable
internal fun AuthScreen(
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // ViewModel и репозиторий собирает Koin (INV-08, INV-22).
    val viewModel: AuthViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                AuthEffect.Authenticated -> onAuthenticated()
                is AuthEffect.ShowMessage ->
                    snackbarHostState.showSnackbar(getString(effect.reason.messageRes()))
            }
        }
    }

    AuthContent(
        state = state,
        onIntent = viewModel::handleIntent,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}
