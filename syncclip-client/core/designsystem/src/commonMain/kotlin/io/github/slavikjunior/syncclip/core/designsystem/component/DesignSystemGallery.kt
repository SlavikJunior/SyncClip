package io.github.slavikjunior.syncclip.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipSpacing
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme

/**
 * Витрина компонентов дизайн-системы.
 *
 * Используется в preview и в screenshot-тестах Roborazzi; все состояния
 * статичны, чтобы baseline не зависел от анимаций.
 */
@Composable
internal fun DesignSystemGallery(modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxSize(),
        color = scheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SyncClipSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SyncClipSpacing.lg),
        ) {
        Text(
            text = "SyncClip design system",
            style = MaterialTheme.typography.headlineMedium,
            color = scheme.onBackground,
        )

        SectionHeader(
            title = "Search",
            trailing = "⌘K",
        )
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search history",
            onClear = { query = "" },
            clearContentDescription = "Clear",
            shortcutHint = "⌘K",
        )

        SectionHeader(
            title = "Statuses",
            trailing = "online",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(label = "Online", state = StatusPillState.ONLINE)
            StatusPill(label = "Offline", state = StatusPillState.OFFLINE)
            StatusPill(
                label = "LAN · 2",
                state = StatusPillState.ONLINE,
                icon = SyncClipIconKind.WIFI,
            )
        }

        SectionHeader(
            title = "Clipboard cards",
            trailing = "3 of 200",
        )
        ClipboardCard(
            text = AnnotatedString("github.com/JetBrains/compose-multiplatform"),
            type = ClipboardCardType.LINK,
            typeLabel = "LINK",
            sourceLabel = "MacBook Pro",
            sourceIcon = SyncClipIconKind.LAPTOP,
            timeLabel = "2 min ago",
            isPinned = true,
            onPinClick = {},
            pinContentDescription = "Unpin",
        )
        ClipboardCard(
            text = AnnotatedString("Check Room migrations on iOS before the release"),
            sourceLabel = "iPhone 15 Pro",
            sourceIcon = SyncClipIconKind.PHONE,
            timeLabel = "12 min ago",
        )
        ClipboardCard(
            text = AnnotatedString("val state = _state.asStateFlow()"),
            type = ClipboardCardType.CODE,
            typeLabel = "CODE · KOTLIN",
            sourceLabel = "iMac",
            timeLabel = "1 h ago",
        )

        SectionHeader(
            title = "Sync",
            trailing = "expressive",
        )
        SyncIndicator(
            title = "All done",
            subtitle = "2 minutes ago",
            state = SyncIndicatorState.IDLE,
        )

        SectionHeader(
            title = "Devices",
            trailing = "3",
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = scheme.surface,
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Column {
                DeviceRow(
                    name = "MacBook Pro",
                    subtitle = "This device",
                    icon = SyncClipIconKind.LAPTOP,
                    statusLabel = "Now",
                    status = StatusPillState.ONLINE,
                )
                HorizontalDivider(color = scheme.outlineVariant)
                DeviceRow(
                    name = "iPhone 15 Pro",
                    subtitle = "Wi-Fi · local network",
                    icon = SyncClipIconKind.PHONE,
                    statusLabel = "2 min",
                    status = StatusPillState.ONLINE,
                )
                HorizontalDivider(color = scheme.outlineVariant)
                DeviceRow(
                    name = "Pixel 9",
                    subtitle = "Offline",
                    icon = SyncClipIconKind.PHONE,
                    statusLabel = "3 h",
                    status = StatusPillState.OFFLINE,
                )
            }
        }

        SectionHeader(
            title = "Settings",
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = scheme.surface,
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Column {
                SettingsRow(
                    title = "Devices",
                    subtitle = "MacBook Pro, iPhone 15 Pro",
                    icon = SyncClipIconKind.DEVICES,
                    value = "3",
                    showChevron = true,
                    onClick = {},
                )
                HorizontalDivider(color = scheme.outlineVariant)
                SettingsRow(
                    title = "Local network",
                    subtitle = "Direct transfer over Wi-Fi",
                    icon = SyncClipIconKind.WIFI,
                    trailing = {
                        Switch(checked = true, onCheckedChange = {})
                    },
                )
                HorizontalDivider(color = scheme.outlineVariant)
                SettingsRow(
                    title = "Theme",
                    subtitle = "Aqua Flow",
                    icon = SyncClipIconKind.MOON,
                    value = "System",
                    showChevron = true,
                    onClick = {},
                )
            }
        }

        EmptyState(
            title = "Nothing here yet",
            message = "Copy something on any device and it will appear here.",
            icon = SyncClipIconKind.CLIPBOARD,
            actionLabel = "Refresh",
            onAction = {},
        )

        SyncClipNavigationBar(
            items = listOf(
                SyncClipNavigationItem(SyncClipIconKind.HISTORY, "History"),
                SyncClipNavigationItem(SyncClipIconKind.SYNC, "Sync"),
                SyncClipNavigationItem(SyncClipIconKind.SETTINGS, "Settings"),
            ),
            selectedIndex = 0,
            onSelect = {},
            modifier = Modifier.fillMaxWidth(),
        )
        }
    }
}

@Preview
@Composable
private fun DesignSystemGalleryPreview() {
    SyncClipTheme {
        DesignSystemGallery()
    }
}
