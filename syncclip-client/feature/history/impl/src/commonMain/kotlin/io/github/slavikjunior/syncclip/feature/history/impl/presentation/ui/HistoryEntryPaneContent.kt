package io.github.slavikjunior.syncclip.feature.history.impl.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIcon
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIconKind
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipMonoFontFamily
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipSpacing
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntryType
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryEntryUi
import io.github.slavikjunior.syncclip.shared.model.DeviceIcon
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import syncclip.feature.history.impl.generated.resources.Res
import syncclip.feature.history.impl.generated.resources.history_copied
import syncclip.feature.history.impl.generated.resources.history_copy
import syncclip.feature.history.impl.generated.resources.history_delete
import syncclip.feature.history.impl.generated.resources.history_pin
import syncclip.feature.history.impl.generated.resources.history_unpin
import kotlin.time.Clock

/** Правая панель адаптивного режима и содержимое экрана детали. */
@Composable
internal fun HistoryEntryPaneContent(
    entry: HistoryEntryUi,
    modifier: Modifier = Modifier,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    onPinClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    val typeLabel = HistoryTypeLabel(entry.type)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SyncClipSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(SyncClipSpacing.lg),
    ) {
        if (typeLabel != null) {
            Text(
                text = typeLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.primary,
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = scheme.surface,
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Text(
                text = entry.text,
                style = if (entry.type == HistoryEntryType.CODE) {
                    MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = SyncClipMonoFontFamily(),
                    )
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = scheme.onSurface,
                modifier = Modifier.padding(SyncClipSpacing.lg),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SyncClipSpacing.sm),
        ) {
            SyncClipIcon(
                kind = entry.sourceIcon.toSyncClipIcon(),
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                size = 16.dp,
            )
            Text(
                text = HistorySourceLabel(entry),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(scheme.outline),
            )
            Text(
                text = HistoryTimeLabel(entry.timestampMillis, nowMillis),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SyncClipSpacing.sm)) {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(entry.text))
                    copied = true
                },
            ) {
                SyncClipIcon(
                    kind = SyncClipIconKind.COPY,
                    contentDescription = null,
                    size = 16.dp,
                )
                Text(
                    text = stringResource(
                        if (copied) Res.string.history_copied else Res.string.history_copy,
                    ),
                )
            }
            TextButton(onClick = { onPinClick?.invoke() }, enabled = onPinClick != null) {
                SyncClipIcon(
                    kind = SyncClipIconKind.PIN,
                    contentDescription = null,
                    size = 16.dp,
                )
                Text(
                    text = stringResource(
                        if (entry.isPinned) Res.string.history_unpin else Res.string.history_pin,
                    ),
                )
            }
            TextButton(onClick = { onDeleteClick?.invoke() }, enabled = onDeleteClick != null) {
                SyncClipIcon(
                    kind = SyncClipIconKind.DELETE,
                    contentDescription = null,
                    size = 16.dp,
                )
                Text(text = stringResource(Res.string.history_delete))
            }
        }
    }
}

@Preview
@Composable
private fun HistoryEntryPaneContentPreview() {
    SyncClipTheme {
        HistoryEntryPaneContent(
            entry = HistoryEntryUi(
                id = "1",
                text = "github.com/JetBrains/compose-multiplatform",
                timestampMillis = Clock.System.now().toEpochMilliseconds() - 2 * 60_000L,
                sourceDeviceId = "mac",
                isPinned = true,
                sourceDeviceName = "MacBook Pro",
                sourceIcon = DeviceIcon.LAPTOP,
                type = HistoryEntryType.LINK,
            ),
            onPinClick = {},
            onDeleteClick = null,
        )
    }
}
