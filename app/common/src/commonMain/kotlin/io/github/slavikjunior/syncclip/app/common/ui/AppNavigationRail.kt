package io.github.slavikjunior.syncclip.app.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipNavigationItem
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipNavigationRail
import org.jetbrains.compose.resources.stringResource

/** Навигация для планшетов и десктопа (medium/expanded). */
@Composable
internal fun AppNavigationRail(
    selected: AppSection,
    onSelect: (AppSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    SyncClipNavigationRail(
        items = AppSection.entries.map { section ->
            SyncClipNavigationItem(
                icon = section.icon,
                label = stringResource(section.label),
            )
        },
        selectedIndex = AppSection.entries.indexOf(selected),
        onSelect = { index -> onSelect(AppSection.entries[index]) },
        modifier = modifier,
    )
}
