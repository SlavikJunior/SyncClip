package io.github.slavikjunior.syncclip.feature.settings.impl.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.component.DeviceRow
import io.github.slavikjunior.syncclip.core.designsystem.component.EmptyState
import io.github.slavikjunior.syncclip.core.designsystem.component.SectionHeader
import io.github.slavikjunior.syncclip.core.designsystem.component.SettingsRow
import io.github.slavikjunior.syncclip.core.designsystem.component.StatusPillState
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIcon
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIconKind
import io.github.slavikjunior.syncclip.core.designsystem.preview.FormFactorPreviews
import io.github.slavikjunior.syncclip.core.designsystem.theme.FontScale
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipSpacing
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeMode
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemePreset
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeState
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceItem
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceKind
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state.DeviceIntent
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state.DeviceManagementUiState
import org.jetbrains.compose.resources.stringResource
import syncclip.feature.settings.impl.generated.resources.Res
import syncclip.feature.settings.impl.generated.resources.settings_appearance
import syncclip.feature.settings.impl.generated.resources.settings_archive
import syncclip.feature.settings.impl.generated.resources.settings_notifications
import syncclip.feature.settings.impl.generated.resources.settings_privacy
import syncclip.feature.settings.impl.generated.resources.settings_security
import syncclip.feature.settings.impl.generated.resources.settings_cancel
import syncclip.feature.settings.impl.generated.resources.settings_devices
import syncclip.feature.settings.impl.generated.resources.settings_autostart
import syncclip.feature.settings.impl.generated.resources.settings_autostart_hint
import syncclip.feature.settings.impl.generated.resources.settings_desktop
import syncclip.feature.settings.impl.generated.resources.settings_history
import syncclip.feature.settings.impl.generated.resources.settings_history_bytes
import syncclip.feature.settings.impl.generated.resources.settings_history_bytes_hint
import syncclip.feature.settings.impl.generated.resources.settings_history_entries
import syncclip.feature.settings.impl.generated.resources.settings_history_entries_hint
import syncclip.feature.settings.impl.generated.resources.settings_history_hint
import syncclip.feature.settings.impl.generated.resources.settings_history_save
import syncclip.feature.settings.impl.generated.resources.settings_history_ttl
import syncclip.feature.settings.impl.generated.resources.settings_history_ttl_hint
import syncclip.feature.settings.impl.generated.resources.settings_limits_error
import syncclip.feature.settings.impl.generated.resources.settings_limits_saved
import syncclip.feature.settings.impl.generated.resources.settings_devices_count
import syncclip.feature.settings.impl.generated.resources.settings_devices_hint
import syncclip.feature.settings.impl.generated.resources.settings_error_load
import syncclip.feature.settings.impl.generated.resources.settings_icon_label
import syncclip.feature.settings.impl.generated.resources.settings_name_label
import syncclip.feature.settings.impl.generated.resources.settings_no_devices
import syncclip.feature.settings.impl.generated.resources.settings_no_devices_hint
import syncclip.feature.settings.impl.generated.resources.settings_pending
import syncclip.feature.settings.impl.generated.resources.settings_rename
import syncclip.feature.settings.impl.generated.resources.settings_retry
import syncclip.feature.settings.impl.generated.resources.settings_save
import syncclip.feature.settings.impl.generated.resources.settings_storage
import syncclip.feature.settings.impl.generated.resources.settings_storage_limit
import syncclip.feature.settings.impl.generated.resources.settings_storage_local
import syncclip.feature.settings.impl.generated.resources.settings_subtitle
import syncclip.feature.settings.impl.generated.resources.settings_this_device
import syncclip.feature.settings.impl.generated.resources.settings_title
import syncclip.feature.settings.impl.generated.resources.settings_version

@Composable
internal fun SettingsContent(
    state: DeviceManagementUiState,
    theme: ThemeState,
    fontScale: FontScale,
    onThemeChange: (ThemeState) -> Unit,
    onRegenerateRandomTheme: () -> Unit,
    onFontScaleChange: (FontScale) -> Unit,
    onIntent: (DeviceIntent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier,
        // Верхний инсет применяет контент (statusBarsPadding), нижний — каркас
        // навигации: иначе статус-бар считался бы дважды (Scaffold отдаёт
        // systemBars через PaddingValues и не потребляет их).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    // На широком окне секции не растягиваются на всю ширину:
                    // строки длиннее 840 dp нечитаемы (адаптивный шаг 4).
                    .widthIn(max = SyncClipSpacing.contentMaxWidth)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SyncClipSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(SyncClipSpacing.lg),
            ) {
                Spacer(modifier = Modifier.height(SyncClipSpacing.xs))
                Column {
                    Text(
                        text = stringResource(Res.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = scheme.onBackground,
                    )
                    Text(
                        text = stringResource(Res.string.settings_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }

                SectionHeader(title = stringResource(Res.string.settings_appearance))
                SettingsAppearanceSection(
                    theme = theme,
                    fontScale = fontScale,
                    onThemeChange = onThemeChange,
                    onRegenerateRandomTheme = onRegenerateRandomTheme,
                    onFontScaleChange = onFontScaleChange,
                )

                SectionHeader(title = stringResource(Res.string.settings_notifications))
                SettingsNotificationsSection(
                    notifications = state.notifications,
                    onIntent = onIntent,
                )

                SectionHeader(title = stringResource(Res.string.settings_privacy))
                SettingsPrivacySection(
                    autoClearSensitive = state.autoClearSensitive,
                    onIntent = onIntent,
                )

                SectionHeader(title = stringResource(Res.string.settings_security))
                SettingsSecuritySection(
                    biometricAvailable = state.biometricAvailable,
                    biometricLock = state.biometricLock,
                    timeoutMinutes = state.biometricTimeoutMinutes,
                    onIntent = onIntent,
                )

                SectionHeader(
                    title = stringResource(Res.string.settings_devices),
                    trailing = stringResource(Res.string.settings_devices_count, state.devices.size),
                )
                Text(
                    text = stringResource(Res.string.settings_devices_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )

                if (state.pendingCount > 0) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = scheme.surface,
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                    ) {
                        SettingsRow(
                            title = stringResource(Res.string.settings_pending, state.pendingCount),
                            icon = SyncClipIconKind.ERROR,
                            trailing = {
                                TextButton(onClick = { onIntent(DeviceIntent.RetryPending) }) {
                                    Text(text = stringResource(Res.string.settings_retry))
                                }
                            },
                        )
                    }
                }

                when {
                    state.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(SyncClipSpacing.lg),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    state.error != null -> {
                        Text(
                            text = stringResource(state.error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.error,
                        )
                    }

                    state.devices.isEmpty() -> {
                        EmptyState(
                            title = stringResource(Res.string.settings_no_devices),
                            message = stringResource(Res.string.settings_no_devices_hint),
                            icon = SyncClipIconKind.DEVICES,
                        )
                    }

                    else -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = scheme.surface,
                            border = BorderStroke(1.dp, scheme.outlineVariant),
                        ) {
                            Column {
                                state.devices.forEachIndexed { index, device ->
                                    DeviceRow(
                                        name = device.name,
                                        subtitle = device.platform,
                                        icon = device.icon.toSyncClipIcon(),
                                        statusLabel = if (device.isCurrent) {
                                            stringResource(Res.string.settings_this_device)
                                        } else {
                                            null
                                        },
                                        status = if (device.isCurrent) {
                                            StatusPillState.ONLINE
                                        } else {
                                            StatusPillState.NEUTRAL
                                        },
                                        onClick = {
                                            onIntent(DeviceIntent.OpenRename(device.id))
                                        },
                                    )
                                    if (index < state.devices.lastIndex) {
                                        HorizontalDivider(color = scheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.autoStartSupported) {
                    SectionHeader(title = stringResource(Res.string.settings_desktop))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = scheme.surface,
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                    ) {
                        SettingsRow(
                            title = stringResource(Res.string.settings_autostart),
                            subtitle = stringResource(Res.string.settings_autostart_hint),
                            icon = SyncClipIconKind.LAPTOP,
                            trailing = {
                                Switch(
                                    checked = state.autoStartEnabled,
                                    enabled = !state.isAutoStartBusy,
                                    onCheckedChange = { onIntent(DeviceIntent.ToggleAutoStart) },
                                )
                            },
                        )
                    }
                }

                SectionHeader(title = stringResource(Res.string.settings_history))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = scheme.surface,
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(SyncClipSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(SyncClipSpacing.sm),
                    ) {
                        Text(
                            text = stringResource(Res.string.settings_history_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                        HistoryLimitField(
                            label = stringResource(Res.string.settings_history_entries),
                            hint = stringResource(Res.string.settings_history_entries_hint),
                            value = state.maxEntriesInput,
                            enabled = !state.isLimitsSaving,
                            onValueChange = { onIntent(DeviceIntent.EntriesChanged(it)) },
                        )
                        HistoryLimitField(
                            label = stringResource(Res.string.settings_history_ttl),
                            hint = stringResource(Res.string.settings_history_ttl_hint),
                            value = state.ttlHoursInput,
                            enabled = !state.isLimitsSaving,
                            onValueChange = { onIntent(DeviceIntent.TtlHoursChanged(it)) },
                        )
                        HistoryLimitField(
                            label = stringResource(Res.string.settings_history_bytes),
                            hint = stringResource(Res.string.settings_history_bytes_hint),
                            value = state.maxBytesMbInput,
                            enabled = !state.isLimitsSaving,
                            onValueChange = { onIntent(DeviceIntent.MaxBytesMbChanged(it)) },
                        )
                        if (state.limitsError != null) {
                            Text(
                                text = stringResource(state.limitsError),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.error,
                            )
                        }
                        Button(
                            onClick = { onIntent(DeviceIntent.SaveHistoryLimits) },
                            enabled = !state.isLimitsSaving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(Res.string.settings_history_save))
                        }
                    }
                }

                SectionHeader(title = stringResource(Res.string.settings_storage))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = scheme.surface,
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                ) {
                    SettingsRow(
                        title = stringResource(Res.string.settings_storage_local),
                        subtitle = stringResource(Res.string.settings_storage_limit),
                        icon = SyncClipIconKind.HISTORY,
                    )
                }

                SectionHeader(title = stringResource(Res.string.settings_archive))
                SettingsArchiveSection(
                    documentsSupported = state.documentsSupported,
                    isBusy = state.isArchiveBusy,
                    exportDialogVisible = state.exportDialogVisible,
                    onIntent = onIntent,
                )

                Text(
                    text = stringResource(Res.string.settings_version),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (state.editingDeviceId != null) {
        RenameDeviceDialog(state = state, onIntent = onIntent)
    }
}
@Composable
private fun RenameDeviceDialog(
    state: DeviceManagementUiState,
    onIntent: (DeviceIntent) -> Unit,
) {
    val nameLabel = stringResource(Res.string.settings_name_label)
    val iconLabel = stringResource(Res.string.settings_icon_label)
    AlertDialog(
        onDismissRequest = { onIntent(DeviceIntent.DismissDialog) },
        title = { Text(text = stringResource(Res.string.settings_rename)) },
        text = {
            Column {
                OutlinedTextField(
                    value = state.draftName,
                    onValueChange = { name -> onIntent(DeviceIntent.NameChanged(name)) },
                    singleLine = true,
                    label = { Text(text = nameLabel) },
                    supportingText = { Text(text = iconLabel) },
                )
                DeviceKind.entries.forEach { icon ->
                    TextButton(onClick = { onIntent(DeviceIntent.IconSelected(icon)) }) {
                        Text(text = stringResource(icon.labelRes()))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onIntent(DeviceIntent.Save) }) {
                Text(text = stringResource(Res.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(DeviceIntent.DismissDialog) }) {
                Text(text = stringResource(Res.string.settings_cancel))
            }
        },
    )
}

private fun DeviceKind.toSyncClipIcon(): SyncClipIconKind = when (this) {
    DeviceKind.PHONE -> SyncClipIconKind.PHONE
    DeviceKind.TABLET -> SyncClipIconKind.TABLET
    DeviceKind.LAPTOP -> SyncClipIconKind.LAPTOP
    DeviceKind.DESKTOP -> SyncClipIconKind.DESKTOP
    DeviceKind.WATCH -> SyncClipIconKind.WATCH
    DeviceKind.TV -> SyncClipIconKind.TV
}

@FormFactorPreviews
@Composable
private fun SettingsContentPreview() {
    SyncClipTheme {
        SettingsContent(
            state = DeviceManagementUiState(
                devices = listOf(
                    DeviceItem(
                        id = "1",
                        name = "MacBook Pro",
                        icon = DeviceKind.LAPTOP,
                        platform = "MACOS",
                        isCurrent = true,
                    ),
                    DeviceItem(
                        id = "2",
                        name = "iPhone 15 Pro",
                        icon = DeviceKind.PHONE,
                        platform = "IOS",
                        isCurrent = false,
                    ),
                ),
            ),
            theme = ThemeState(),
            fontScale = FontScale.MEDIUM,
            onThemeChange = {},
            onRegenerateRandomTheme = {},
            onFontScaleChange = {},
            onIntent = {},
        )
    }
}

/** Числовое поле лимита истории с подсказкой диапазона (FR-4.3). */
@Composable
private fun HistoryLimitField(
    label: String,
    hint: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() }) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = label) },
        supportingText = { Text(text = hint) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
