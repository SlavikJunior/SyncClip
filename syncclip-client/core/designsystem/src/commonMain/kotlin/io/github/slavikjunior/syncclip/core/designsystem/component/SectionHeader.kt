package io.github.slavikjunior.syncclip.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme

/** Заголовок секции с необязательным действием справа. */
@Composable
public fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = if (onTrailingClick != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                modifier = Modifier
                    .then(
                        if (onTrailingClick != null) {
                            Modifier
                                .clickable(onClick = onTrailingClick)
                                .padding(start = 8.dp)
                        } else {
                            Modifier.padding(start = 8.dp)
                        },
                    ),
            )
        }
    }
}

@Preview
@Composable
private fun SectionHeaderPreview() {
    SyncClipTheme {
        SectionHeader(
            title = "Recent",
            trailing = "10 of 200",
            modifier = Modifier.padding(16.dp),
        )
    }
}
