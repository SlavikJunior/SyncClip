package io.github.slavikjunior.syncclip.app.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipNavigationBar
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipNavigationItem
import org.jetbrains.compose.resources.stringResource

/** Нижняя навигация для компактных экранов (телефон). */
@Composable
internal fun AppNavigationBar(
    selected: AppSection,
    onSelect: (AppSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    SyncClipNavigationBar(
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
