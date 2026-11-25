package io.github.slavikjunior.syncclip.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme

/** Нижняя навигация приложения с pill-индикатором активного раздела. */
@Composable
public fun SyncClipNavigationBar(
    items: List<SyncClipNavigationItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .selectableGroup()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

@Composable
private fun RowScope.NavigationBarItem(
    item: SyncClipNavigationItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val contentColor = if (selected) scheme.primary else scheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.medium)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Column(
            modifier = Modifier
                .size(width = 52.dp, height = 28.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) scheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SyncClipIcon(
                kind = item.icon,
                contentDescription = null,
                tint = contentColor,
                size = 22.dp,
            )
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun SyncClipNavigationBarPreview() {
    SyncClipTheme {
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
