package io.github.slavikjunior.syncclip.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipSpacing
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme

/**
 * Поисковая строка с иконкой, очисткой и подсказкой шортката.
 */
@Composable
public fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    clearContentDescription: String? = null,
    shortcutHint: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = if (focused) scheme.primary else scheme.outlineVariant

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, borderColor, shape),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.primary),
        interactionSource = interaction,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = SyncClipSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SyncClipSpacing.sm),
            ) {
                SyncClipIcon(
                    kind = SyncClipIconKind.SEARCH,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    size = 18.dp,
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty() && onClear != null) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClear),
                        contentAlignment = Alignment.Center,
                    ) {
                        SyncClipIcon(
                            kind = SyncClipIconKind.CLOSE,
                            contentDescription = clearContentDescription,
                            tint = scheme.onSurfaceVariant,
                            size = 16.dp,
                        )
                    }
                }
                if (shortcutHint != null) {
                    Text(
                        text = shortcutHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(scheme.surfaceVariant)
                            .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        },
    )
}

@Preview
@Composable
private fun SearchFieldPreview() {
    SyncClipTheme {
        var query by remember { mutableStateOf("") }
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search history",
            onClear = { query = "" },
            clearContentDescription = "Clear",
            shortcutHint = "⌘K",
            modifier = Modifier.padding(SyncClipSpacing.lg),
        )
    }
}
