package io.github.slavikjunior.syncclip.feature.settings.impl.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipSpacing
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.ArchiveFormat
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state.DeviceIntent
import org.jetbrains.compose.resources.stringResource
import syncclip.feature.settings.impl.generated.resources.Res
import syncclip.feature.settings.impl.generated.resources.settings_archive_export
import syncclip.feature.settings.impl.generated.resources.settings_archive_format_csv
import syncclip.feature.settings.impl.generated.resources.settings_archive_format_json
import syncclip.feature.settings.impl.generated.resources.settings_archive_hint
import syncclip.feature.settings.impl.generated.resources.settings_archive_import
import syncclip.feature.settings.impl.generated.resources.settings_archive_warning
import syncclip.feature.settings.impl.generated.resources.settings_cancel

/**
 * Секция архива истории (US-018, US-024).
 *
 * Экспорт открывает диалог формата и предупреждение о незашифрованном файле,
 * импорт сразу вызывает системный диалог выбора файла. Кнопки скрыты, если
 * платформа не умеет открывать диалог файлов.
 */
@Composable
internal fun SettingsArchiveSection(
    documentsSupported: Boolean,
    isBusy: Boolean,
    exportDialogVisible: Boolean,
    onIntent: (DeviceIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(SyncClipSpacing.md),
            verticalArrangement = Arrangement.spacedBy(SyncClipSpacing.sm),
        ) {
            Text(
                text = stringResource(Res.string.settings_archive_hint),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.settings_archive_warning),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.error,
            )
            if (!documentsSupported) return@Column
            Row(horizontalArrangement = Arrangement.spacedBy(SyncClipSpacing.sm)) {
                Button(
                    onClick = { onIntent(DeviceIntent.OpenExportDialog) },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(Res.string.settings_archive_export))
                }
                OutlinedButton(
                    onClick = { onIntent(DeviceIntent.ImportHistory) },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(Res.string.settings_archive_import))
                }
            }
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.padding(SyncClipSpacing.xs))
            }
        }
    }

    if (exportDialogVisible) {
        ArchiveFormatDialog(onIntent = onIntent)
    }
}

@Composable
private fun ArchiveFormatDialog(onIntent: (DeviceIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(DeviceIntent.DismissExportDialog) },
        title = { Text(text = stringResource(Res.string.settings_archive_export)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SyncClipSpacing.sm)) {
                Text(
                    text = stringResource(Res.string.settings_archive_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(SyncClipSpacing.sm)) {
                    Button(
                        onClick = { onIntent(DeviceIntent.ExportHistory(ArchiveFormat.JSON)) },
                    ) {
                        Text(text = stringResource(Res.string.settings_archive_format_json))
                    }
                    OutlinedButton(
                        onClick = { onIntent(DeviceIntent.ExportHistory(ArchiveFormat.CSV)) },
                    ) {
                        Text(text = stringResource(Res.string.settings_archive_format_csv))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { onIntent(DeviceIntent.DismissExportDialog) }) {
                Text(text = stringResource(Res.string.settings_cancel))
            }
        },
    )
}

@Preview(widthDp = 420)
@Composable
private fun SettingsArchiveSectionPreview() {
    SyncClipTheme {
        SettingsArchiveSection(
            documentsSupported = true,
            isBusy = false,
            exportDialogVisible = false,
            onIntent = {},
        )
    }
}
