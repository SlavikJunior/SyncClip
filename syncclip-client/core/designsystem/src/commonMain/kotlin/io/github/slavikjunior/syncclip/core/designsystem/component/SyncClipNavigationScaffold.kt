package io.github.slavikjunior.syncclip.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import io.github.slavikjunior.syncclip.core.designsystem.adaptive.isWideWindow
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme

/**
 * Каркас приложения с адаптивной областью навигации.
 *
 * Замена `NavigationSuiteScaffold` из `material3-adaptive-navigation-suite`:
 * артефакт публикуется только для Android (у Google в 1.5.0 нет iOS/desktop
 * вариантов, у JetBrains его нет вовсе), а общий экранный слой собирается в
 * `commonMain` для Android, iOS и Desktop.
 *
 * COMPACT — нижняя панель (телефон в портрете), MEDIUM/EXPANDED — боковой
 * rail (планшет, складной, десктоп). Каркас владеет нижним системным инсетом
 * в обоих режимах: в COMPACT его закрывает сама панель
 * (`navigationBarsPadding` внутри), в остальных — отступ контента. Верхний
 * инсет экраны обрабатывают сами (`statusBarsPadding` в контенте), поэтому у
 * бара и rail он тоже не считается дважды.
 */
@Composable
public fun SyncClipNavigationScaffold(
    items: List<SyncClipNavigationItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (!currentWindowAdaptiveInfoV2().windowSizeClass.isWideWindow()) {
        Scaffold(
            modifier = modifier,
            // Инсеты обрабатывают панель и контент: верх — экраны, низ — панель.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                SyncClipNavigationBar(
                    items = items,
                    selectedIndex = selectedIndex,
                    onSelect = onSelect,
                )
            },
            content = content,
        )
    } else {
        Row(modifier = modifier.fillMaxSize()) {
            SyncClipNavigationRail(
                items = items,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // Вбоком режиме системная панель снизу не занята баром:
                    // отступ отдаём контенту, чтобы его не перекрывало.
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
                    ),
            ) {
                content(PaddingValues(0.dp))
            }
        }
    }
}

@Preview(name = "Compact", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SyncClipNavigationScaffoldCompactPreview() {
    SyncClipTheme {
        SyncClipNavigationScaffold(
            items = PreviewNavigationItems,
            selectedIndex = 0,
            onSelect = {},
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize())
        }
    }
}

@Preview(name = "Expanded", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun SyncClipNavigationScaffoldExpandedPreview() {
    SyncClipTheme {
        SyncClipNavigationScaffold(
            items = PreviewNavigationItems,
            selectedIndex = 0,
            onSelect = {},
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize())
        }
    }
}

private val PreviewNavigationItems = listOf(
    SyncClipNavigationItem(SyncClipIconKind.HISTORY, "History"),
    SyncClipNavigationItem(SyncClipIconKind.SYNC, "Sync"),
    SyncClipNavigationItem(SyncClipIconKind.SETTINGS, "Settings"),
)
