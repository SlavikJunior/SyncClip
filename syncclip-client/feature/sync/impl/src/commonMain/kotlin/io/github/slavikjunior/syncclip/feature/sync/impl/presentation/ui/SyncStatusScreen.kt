package io.github.slavikjunior.syncclip.feature.sync.impl.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.slavikjunior.syncclip.feature.sync.impl.presentation.state.SyncStatusViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SyncStatusScreen(
    modifier: Modifier = Modifier,
) {
    // Зависимости и ViewModel собирает Koin (INV-08, INV-22).
    val viewModel: SyncStatusViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    SyncStatusContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::handleIntent,
    )
}
