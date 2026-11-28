package io.github.slavikjunior.syncclip.feature.history.impl.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.component.EmptyState
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIcon
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIconKind
import io.github.slavikjunior.syncclip.core.designsystem.preview.FormFactorPreviews
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntryType
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryDetailIntent
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryDetailUiState
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryEntryUi
import io.github.slavikjunior.syncclip.shared.model.DeviceIcon
import org.jetbrains.compose.resources.stringResource
import syncclip.feature.history.impl.generated.resources.Res
import syncclip.feature.history.impl.generated.resources.history_back
import syncclip.feature.history.impl.generated.resources.history_detail_empty
import syncclip.feature.history.impl.generated.resources.history_detail_title
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryDetailContent(
    state: HistoryDetailUiState,
    onIntent: (HistoryDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    showBackArrow: Boolean = true,
) {
    // Адаптивный шаг 5: app bar уезжает при скролле вниз и возвращается при
    // скролле вверх — на узком экране деталь читается на всю высоту.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.history_detail_title)) },
                navigationIcon = {
                    // В list-detail (широкое окно) деталь — панель, а не экран.
                    if (showBackArrow) {
                        IconButton(onClick = onBack) {
                            SyncClipIcon(
                                kind = SyncClipIconKind.CHEVRON_RIGHT,
                                contentDescription = stringResource(Res.string.history_back),
                                modifier = Modifier.rotate(180f),
                                size = 22.dp,
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val entry = state.entry
        if (entry == null) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = stringResource(Res.string.history_detail_empty),
                    message = stringResource(Res.string.history_detail_empty),
                    icon = SyncClipIconKind.HISTORY,
                )
            }
        } else {
            HistoryEntryPaneContent(
                entry = entry,
                modifier = Modifier.padding(padding),
                nowMillis = nowMillis,
                onPinClick = { onIntent(HistoryDetailIntent.TogglePin) },
                onDeleteClick = if (entry.isPinned) {
                    null
                } else {
                    { onIntent(HistoryDetailIntent.Delete) }
                },
            )
        }
    }
}

@FormFactorPreviews
@Composable
private fun HistoryDetailContentPreview() {
    SyncClipTheme {
        HistoryDetailContent(
            state = HistoryDetailUiState(
                entry = HistoryEntryUi(
                    id = "1",
                    text = "Check Room migrations on iOS before the release",
                    timestampMillis = Clock.System.now().toEpochMilliseconds() - 12 * 60_000L,
                    sourceDeviceId = "phone",
                    isPinned = false,
                    sourceDeviceName = "iPhone 15 Pro",
                    sourceIcon = DeviceIcon.PHONE,
                    type = HistoryEntryType.TEXT,
                ),
                isLoading = false,
            ),
            onIntent = {},
        )
    }
}
