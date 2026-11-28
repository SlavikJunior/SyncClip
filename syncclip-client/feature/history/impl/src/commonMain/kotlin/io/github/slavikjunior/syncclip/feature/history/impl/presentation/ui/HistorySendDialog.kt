package io.github.slavikjunior.syncclip.feature.history.impl.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIcon
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipSpacing
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryIntent
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.SendDialogUiState
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.SendTargetUi
import io.github.slavikjunior.syncclip.shared.model.DeviceIcon
import org.jetbrains.compose.resources.stringResource
import syncclip.feature.history.impl.generated.resources.Res
import syncclip.feature.history.impl.generated.resources.history_send_cancel
import syncclip.feature.history.impl.generated.resources.history_send_confirm
import syncclip.feature.history.impl.generated.resources.history_send_hint
import syncclip.feature.history.impl.generated.resources.history_send_title

/**
 * Диалог адресной отправки (US-016).
 *
 * Чеклист устройств: по умолчанию выбраны все, кроме текущего, кнопка
 * «Отправить» недоступна без выбора. Состояние приходит из ViewModel — диалог
 * stateless и рендерится в превью без DI.
 */
@Composable
internal fun HistorySendDialog(
    state: SendDialogUiState,
    onIntent: (HistoryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = { onIntent(HistoryIntent.DismissSendTargets) },
        title = { Text(text = stringResource(Res.string.history_send_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SyncClipSpacing.xs),
            ) {
                Text(
                    text = stringResource(Res.string.history_send_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(SyncClipSpacing.xs))
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(SyncClipSpacing.md))
                } else {
                    state.targets.forEach { target ->
                        SendTargetRow(
                            target = target,
                            checked = target.id in state.selectedIds,
                            onToggle = { onIntent(HistoryIntent.ToggleSendTarget(target.id)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onIntent(HistoryIntent.ConfirmSend) },
                enabled = state.canSend,
            ) {
                Text(text = stringResource(Res.string.history_send_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(HistoryIntent.DismissSendTargets) }) {
                Text(text = stringResource(Res.string.history_send_cancel))
            }
        },
    )
}

@Composable
private fun SendTargetRow(
    target: SendTargetUi,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Вся строка — цель нажатия: чекбокс и имя не разъезжаются.
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SyncClipSpacing.sm),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        SyncClipIcon(
            kind = target.icon.toSyncClipIcon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = 16.dp,
        )
        Text(
            text = target.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview
@Composable
private fun HistorySendDialogPreview() {
    SyncClipTheme {
        HistorySendDialog(
            state = SendDialogUiState(
                targets = listOf(
                    SendTargetUi(id = "1", name = "iPhone 15 Pro", icon = DeviceIcon.PHONE),
                    SendTargetUi(id = "2", name = "MacBook Pro Work", icon = DeviceIcon.LAPTOP),
                ),
                selectedIds = setOf("1"),
            ),
            onIntent = {},
        )
    }
}
