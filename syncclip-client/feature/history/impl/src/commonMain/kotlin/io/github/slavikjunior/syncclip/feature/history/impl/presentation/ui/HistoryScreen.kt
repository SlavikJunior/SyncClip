package io.github.slavikjunior.syncclip.feature.history.impl.presentation.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryEffect
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryIntent
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryViewModel
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import syncclip.feature.history.impl.generated.resources.Res
import syncclip.feature.history.impl.generated.resources.history_undo
import syncclip.feature.history.impl.generated.resources.history_undo_action

/**
 * Route-слой: единственное место, где знаю про ViewModel и навигацию.
 * В commonMain используется collectAsState() (разрешение профиля INV-15).
 *
 * Двухпанельный режим не выбирается здесь: его раскладывает
 * `ListDetailSceneStrategy` в `app:common` (адаптивный шаг 3).
 */
@Composable
internal fun HistoryScreen(
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // ViewModel приходит из Koin: живёт в ViewModelStore и отменяет viewModelScope (INV-08).
    val viewModel: HistoryViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val undoMessage = stringResource(Res.string.history_undo)
    val undoAction = stringResource(Res.string.history_undo_action)
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is HistoryEffect.ShowUndo -> {
                    val result = snackbarHostState.showSnackbar(
                        message = undoMessage,
                        actionLabel = undoAction,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.handleIntent(HistoryIntent.Restore(effect.entry))
                    }
                }

                is HistoryEffect.NavigateToDetail -> onOpenDetail(effect.id)
                is HistoryEffect.ShowMessage -> snackbarHostState.showSnackbar(
                    message = getString(effect.message),
                )
            }
        }
    }

    HistoryContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::handleIntent,
        snackbarHostState = snackbarHostState,
    )
}
