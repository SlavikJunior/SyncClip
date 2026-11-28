package io.github.slavikjunior.syncclip.feature.history.impl.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.slavikjunior.syncclip.core.designsystem.component.EmptyState
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIconKind
import io.github.slavikjunior.syncclip.core.designsystem.preview.FormFactorPreviews
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import org.jetbrains.compose.resources.stringResource
import syncclip.feature.history.impl.generated.resources.Res
import syncclip.feature.history.impl.generated.resources.history_select_entry
import syncclip.feature.history.impl.generated.resources.history_select_entry_hint

/**
 * Заглушка правой панели list-detail, пока запись не выбрана (адаптивный шаг 3).
 *
 * Её передаёт `ListDetailSceneStrategy.listPane(detailPlaceholder = ...)`,
 * поэтому на узком окне она не показывается вовсе.
 */
@Composable
internal fun HistoryDetailPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            title = stringResource(Res.string.history_select_entry),
            message = stringResource(Res.string.history_select_entry_hint),
            icon = SyncClipIconKind.HISTORY,
        )
    }
}

@FormFactorPreviews
@Composable
private fun HistoryDetailPlaceholderPreview() {
    SyncClipTheme {
        HistoryDetailPlaceholder()
    }
}
