package io.github.slavikjunior.syncclip.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.theme.LocalSyncClipColors
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipSpacing
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme

/**
 * Строка устройства: иконка платформы, имя, подпись и статус.
 *
 * Компонент не рисует фон — экран складывает строки в общую группу с
 * разделителями.
 */
@Composable
public fun DeviceRow(
    name: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: SyncClipIconKind = SyncClipIconKind.LAPTOP,
    statusLabel: String? = null,
    status: StatusPillState = StatusPillState.ONLINE,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val extended = LocalSyncClipColors.current
    val dotColor = when (status) {
        StatusPillState.ONLINE -> extended.online
        StatusPillState.OFFLINE -> extended.offline
        StatusPillState.SYNCING -> scheme.primary
        StatusPillState.ERROR -> scheme.error
        StatusPillState.NEUTRAL -> scheme.outline
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = SyncClipSpacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SyncClipSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(MaterialTheme.shapes.small)
                .background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            SyncClipIcon(
                kind = icon,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                size = 19.dp,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (statusLabel != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview
@Composable
private fun DeviceRowPreview() {
    SyncClipTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            DeviceRow(
                name = "MacBook Pro",
                subtitle = "This device",
                icon = SyncClipIconKind.LAPTOP,
                statusLabel = "Now",
                status = StatusPillState.ONLINE,
            )
            DeviceRow(
                name = "iPhone 15 Pro",
                subtitle = "Wi-Fi · local network",
                icon = SyncClipIconKind.PHONE,
                statusLabel = "2 min",
                status = StatusPillState.ONLINE,
            )
            DeviceRow(
                name = "Pixel 9",
                subtitle = "Offline",
                icon = SyncClipIconKind.PHONE,
                statusLabel = "3 h",
                status = StatusPillState.OFFLINE,
            )
        }
    }
}
