package io.github.slavikjunior.syncclip.feature.history.impl.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.slavikjunior.syncclip.feature.history.impl.domain.repository.HistoryRepository
import io.github.slavikjunior.syncclip.shared.model.DeviceDirectory
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryDetailEffect
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryDetailIntent
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryDetailViewModel
import org.koin.compose.koinInject

@Composable
internal fun HistoryDetailScreen(
    eventId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository: HistoryRepository = koinInject()
    val deviceDirectory: DeviceDirectory = koinInject()
    val viewModel = remember(eventId) { HistoryDetailViewModel(eventId, repository, deviceDirectory) }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.handleIntent(HistoryDetailIntent.Load)
        viewModel.effect.collect { effect ->
            when (effect) {
                HistoryDetailEffect.NavigateBack -> onBack()
            }
        }
    }

    HistoryDetailContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::handleIntent,
        onBack = onBack,
    )
}
