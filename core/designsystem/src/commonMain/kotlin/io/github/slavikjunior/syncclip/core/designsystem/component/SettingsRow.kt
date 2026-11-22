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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipSpacing
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme

/**
 * Строка настроек: иконка, заголовок, подпись, значение и необязательный
 * trailing-слот (переключатель, пресеты и т.п.).
 */
@Composable
public fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: SyncClipIconKind? = null,
    value: String? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
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
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(scheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                SyncClipIcon(
                    kind = icon,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    size = 17.dp,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
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
        if (trailing != null) {
            trailing()
        } else {
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (showChevron) {
                SyncClipIcon(
                    kind = SyncClipIconKind.CHEVRON_RIGHT,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    size = 15.dp,
                )
            }
        }
    }
}

@Preview
@Composable
private fun SettingsRowPreview() {
    SyncClipTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            SettingsRow(
                title = "Devices",
                subtitle = "MacBook Pro, iPhone 15 Pro",
                icon = SyncClipIconKind.DEVICES,
                value = "3",
                showChevron = true,
            )
            SettingsRow(
                title = "Local network",
                subtitle = "Direct transfer over Wi-Fi",
                icon = SyncClipIconKind.WIFI,
                trailing = {
                    Box(
                        modifier = Modifier
                            .size(40.dp, 24.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                },
            )
        }
    }
}
