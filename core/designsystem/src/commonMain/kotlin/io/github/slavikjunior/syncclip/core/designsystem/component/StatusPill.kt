package io.github.slavikjunior.syncclip.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.theme.LocalSyncClipColors
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme

/**
 * Компактный статус: точка + подпись или иконка + подпись.
 */
@Composable
public fun StatusPill(
    label: String,
    modifier: Modifier = Modifier,
    state: StatusPillState = StatusPillState.NEUTRAL,
    icon: SyncClipIconKind? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val extended = LocalSyncClipColors.current
    val dotColor = when (state) {
        StatusPillState.ONLINE -> extended.online
        StatusPillState.OFFLINE -> extended.offline
        StatusPillState.SYNCING -> scheme.primary
        StatusPillState.ERROR -> scheme.error
        StatusPillState.NEUTRAL -> scheme.outline
    }
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(scheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            SyncClipIcon(
                kind = icon,
                contentDescription = null,
                tint = dotColor,
                size = 13.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Preview
@Composable
private fun StatusPillPreview() {
    SyncClipTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            StatusPill(label = "Online", state = StatusPillState.ONLINE)
            StatusPill(label = "Offline", state = StatusPillState.OFFLINE)
            StatusPill(
                label = "LAN · 2 devices",
                state = StatusPillState.ONLINE,
                icon = SyncClipIconKind.WIFI,
            )
        }
    }
}
