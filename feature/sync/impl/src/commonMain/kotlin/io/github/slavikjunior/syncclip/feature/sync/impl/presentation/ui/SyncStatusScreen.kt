package io.github.slavikjunior.syncclip.feature.sync.impl.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository.ClipboardPacketSource
import io.github.slavikjunior.syncclip.shared.model.DeviceDirectory
import io.github.slavikjunior.syncclip.feature.sync.impl.presentation.state.SyncIntent
import io.github.slavikjunior.syncclip.feature.sync.impl.presentation.state.SyncStatusViewModel
import org.koin.compose.koinInject

@Composable
internal fun SyncStatusScreen(
    modifier: Modifier = Modifier,
) {
    val repository: ClipboardPacketSource = koinInject()
    val deviceDirectory: DeviceDirectory = koinInject()
    val viewModel = remember { SyncStatusViewModel(repository, deviceDirectory) }
    val state by viewModel.state.collectAsState()

    SyncStatusContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::handleIntent,
    )
}
