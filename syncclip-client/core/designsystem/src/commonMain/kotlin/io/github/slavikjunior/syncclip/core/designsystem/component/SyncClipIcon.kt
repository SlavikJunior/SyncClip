package io.github.slavikjunior.syncclip.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme

/**
 * Векторная иконка SyncClip.
 *
 * [contentDescription] обязателен осознанно: для декоративных иконок передайте
 * `null`, для интерактивных — локализованную строку.
 */
@Composable
public fun SyncClipIcon(
    kind: SyncClipIconKind,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 20.dp,
) {
    Icon(
        imageVector = iconVector(kind),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

private val iconCache = mutableMapOf<SyncClipIconKind, ImageVector>()

private fun iconVector(kind: SyncClipIconKind): ImageVector = iconCache.getOrPut(kind) {
    ImageVector.Builder(
        name = kind.name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    )
        .addGroup(translationY = 960f)
        .addPath(
            pathData = PathParser().parsePathString(kind.pathData).toNodes(),
            fill = SolidColor(Color.Black),
        )
        .build()
}

@Preview
@Composable
private fun SyncClipIconPreview() {
    SyncClipTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            listOf(
                SyncClipIconKind.SEARCH,
                SyncClipIconKind.LINK,
                SyncClipIconKind.COPY,
                SyncClipIconKind.PIN,
                SyncClipIconKind.LAPTOP,
                SyncClipIconKind.PHONE,
                SyncClipIconKind.SYNC,
                SyncClipIconKind.HISTORY,
            ).forEach { kind ->
                SyncClipIcon(
                    kind = kind,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
