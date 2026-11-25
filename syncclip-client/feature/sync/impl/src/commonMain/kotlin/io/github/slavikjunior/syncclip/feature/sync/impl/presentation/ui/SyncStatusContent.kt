package io.github.slavikjunior.syncclip.feature.sync.impl.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.component.ClipboardCard
import io.github.slavikjunior.syncclip.core.designsystem.component.DeviceRow
import io.github.slavikjunior.syncclip.core.designsystem.component.EmptyState
import io.github.slavikjunior.syncclip.core.designsystem.component.SectionHeader
import io.github.slavikjunior.syncclip.core.designsystem.component.StatusPill
import io.github.slavikjunior.syncclip.core.designsystem.component.StatusPillState
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIcon
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIconKind
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncIndicator
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncIndicatorState
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipSpacing
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.feature.sync.impl.presentation.state.SyncDeviceUi
import io.github.slavikjunior.syncclip.feature.sync.impl.presentation.state.SyncIntent
import io.github.slavikjunior.syncclip.feature.sync.impl.presentation.state.SyncStatusUiState
import io.github.slavikjunior.syncclip.shared.model.DeviceIcon
import org.jetbrains.compose.resources.stringResource
import syncclip.feature.sync.impl.generated.resources.Res
import syncclip.feature.sync.impl.generated.resources.sync_devices
import syncclip.feature.sync.impl.generated.resources.sync_devices_count
import syncclip.feature.sync.impl.generated.resources.sync_done_title
import syncclip.feature.sync.impl.generated.resources.sync_empty
import syncclip.feature.sync.impl.generated.resources.sync_indicator_description
import syncclip.feature.sync.impl.generated.resources.sync_just_now
import syncclip.feature.sync.impl.generated.resources.sync_lan_few
import syncclip.feature.sync.impl.generated.resources.sync_lan_many
import syncclip.feature.sync.impl.generated.resources.sync_lan_one
import syncclip.feature.sync.impl.generated.resources.sync_last_transfer
import syncclip.feature.sync.impl.generated.resources.sync_no_devices
import syncclip.feature.sync.impl.generated.resources.sync_no_devices_hint
import syncclip.feature.sync.impl.generated.resources.sync_offline
import syncclip.feature.sync.impl.generated.resources.sync_offline_title
import syncclip.feature.sync.impl.generated.resources.sync_online
import syncclip.feature.sync.impl.generated.resources.sync_packets_few
import syncclip.feature.sync.impl.generated.resources.sync_packets_many
import syncclip.feature.sync.impl.generated.resources.sync_packets_one
import syncclip.feature.sync.impl.generated.resources.sync_pause
import syncclip.feature.sync.impl.generated.resources.sync_resume
import syncclip.feature.sync.impl.generated.resources.sync_source
import syncclip.feature.sync.impl.generated.resources.sync_subtitle
import syncclip.feature.sync.impl.generated.resources.sync_this_device
import syncclip.feature.sync.impl.generated.resources.sync_title

@Composable
internal fun SyncStatusContent(
    state: SyncStatusUiState,
    onIntent: (SyncIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onIntent(SyncIntent.StartListening) }

    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SyncClipSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(SyncClipSpacing.lg),
        ) {
            Spacer(modifier = Modifier.height(SyncClipSpacing.xs))
            SyncHeader()
            SyncHero(state = state, onIntent = onIntent)
            SectionHeader(
                title = stringResource(Res.string.sync_devices),
                trailing = stringResource(Res.string.sync_devices_count, state.devices.size),
            )
            SyncDevices(state = state)
            SectionHeader(title = stringResource(Res.string.sync_last_transfer))
            LastTransfer(state = state)
        }
    }
}

@Composable
private fun SyncHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.sync_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(Res.string.sync_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(contentAlignment = Alignment.Center) {
                SyncClipIcon(
                    kind = SyncClipIconKind.MORE,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 20.dp,
                )
            }
        }
    }
}

@Composable
private fun SyncHero(
    state: SyncStatusUiState,
    onIntent: (SyncIntent) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val title = if (state.isOnline) {
        stringResource(Res.string.sync_done_title)
    } else {
        stringResource(Res.string.sync_offline_title)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SyncClipSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SyncClipSpacing.md),
        ) {
            SyncIndicator(
                title = title,
                subtitle = PacketsLabel(state.packetCount),
                state = if (state.isOnline) {
                    SyncIndicatorState.IDLE
                } else {
                    SyncIndicatorState.OFFLINE
                },
                contentDescription = stringResource(
                    Res.string.sync_indicator_description,
                    title,
                ),
            )
            if (state.isOnline) {
                OutlinedButton(
                    onClick = { onIntent(SyncIntent.ToggleOnline) },
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    SyncClipIcon(
                        kind = SyncClipIconKind.SYNC,
                        contentDescription = null,
                        size = 16.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(Res.string.sync_pause))
                }
            } else {
                Button(
                    onClick = { onIntent(SyncIntent.ToggleOnline) },
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    SyncClipIcon(
                        kind = SyncClipIconKind.SYNC,
                        contentDescription = null,
                        size = 16.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(Res.string.sync_resume))
                }
            }
            StatusPill(
                label = LanLabel(state.devices.size),
                state = if (state.isOnline) StatusPillState.ONLINE else StatusPillState.OFFLINE,
                icon = SyncClipIconKind.WIFI,
            )
        }
    }
}

@Composable
private fun SyncDevices(state: SyncStatusUiState) {
    val scheme = MaterialTheme.colorScheme
    when {
        state.isLoadingDevices -> {
            Box(
                modifier = Modifier.fillMaxWidth().padding(SyncClipSpacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        state.devices.isEmpty() -> {
            EmptyState(
                title = stringResource(Res.string.sync_no_devices),
                message = stringResource(Res.string.sync_no_devices_hint),
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
                            subtitle = if (device.isCurrent) {
                                stringResource(Res.string.sync_this_device)
                            } else {
                                null
                            },
                            icon = device.icon.toSyncClipIcon(),
                            statusLabel = if (state.isOnline) {
                                stringResource(Res.string.sync_online)
                            } else {
                                stringResource(Res.string.sync_offline)
                            },
                            status = if (state.isOnline) {
                                StatusPillState.ONLINE
                            } else {
                                StatusPillState.OFFLINE
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
}

@Composable
private fun LastTransfer(state: SyncStatusUiState) {
    if (state.lastText.isBlank()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Text(
                text = stringResource(Res.string.sync_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(SyncClipSpacing.lg),
            )
        }
    } else {
        ClipboardCard(
            text = AnnotatedString(state.lastText),
            sourceLabel = stringResource(Res.string.sync_source),
            sourceIcon = SyncClipIconKind.CLIPBOARD,
            timeLabel = stringResource(Res.string.sync_just_now),
        )
    }
}

private fun DeviceIcon.toSyncClipIcon(): SyncClipIconKind = when (this) {
    DeviceIcon.PHONE -> SyncClipIconKind.PHONE
    DeviceIcon.TABLET -> SyncClipIconKind.TABLET
    DeviceIcon.LAPTOP -> SyncClipIconKind.LAPTOP
    DeviceIcon.DESKTOP -> SyncClipIconKind.DESKTOP
    DeviceIcon.WATCH -> SyncClipIconKind.WATCH
    DeviceIcon.TV -> SyncClipIconKind.TV
}

@Composable
private fun PacketsLabel(count: Int): String = when (pluralIndex(count)) {
    0 -> stringResource(Res.string.sync_packets_one, count)
    1 -> stringResource(Res.string.sync_packets_few, count)
    else -> stringResource(Res.string.sync_packets_many, count)
}

@Composable
private fun LanLabel(count: Int): String = when (pluralIndex(count)) {
    0 -> stringResource(Res.string.sync_lan_one, count)
    1 -> stringResource(Res.string.sync_lan_few, count)
    else -> stringResource(Res.string.sync_lan_many, count)
}

private fun pluralIndex(count: Int): Int {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> 2
        mod10 == 1 -> 0
        mod10 in 2..4 -> 1
        else -> 2
    }
}

@Preview
@Composable
private fun SyncStatusContentPreview() {
    SyncClipTheme {
        SyncStatusContent(
            state = SyncStatusUiState(
                isOnline = true,
                packetCount = 3,
                devices = listOf(
                    SyncDeviceUi("1", "MacBook Pro", DeviceIcon.LAPTOP, isCurrent = true),
                    SyncDeviceUi("2", "iPhone 15 Pro", DeviceIcon.PHONE),
                    SyncDeviceUi("3", "Pixel 9", DeviceIcon.PHONE),
                ),
            ),
            onIntent = {},
        )
    }
}
