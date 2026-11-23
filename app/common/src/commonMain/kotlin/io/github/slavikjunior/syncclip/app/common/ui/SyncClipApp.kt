package io.github.slavikjunior.syncclip.app.common.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.window.core.layout.WindowWidthSizeClass
import io.github.slavikjunior.syncclip.app.common.navigation.AppNavigator
import io.github.slavikjunior.syncclip.app.common.ui.platform.PlatformBackHandler
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeState
import io.github.slavikjunior.syncclip.feature.history.api.navigation.HistoryDetailRoute
import io.github.slavikjunior.syncclip.feature.history.api.navigation.HistoryRoute
import io.github.slavikjunior.syncclip.feature.history.impl.historyDetailNavEntry
import io.github.slavikjunior.syncclip.feature.history.impl.historyListNavEntry
import io.github.slavikjunior.syncclip.feature.settings.impl.settingsNavEntry
import io.github.slavikjunior.syncclip.feature.sync.impl.syncNavEntry

/**
 * Корневой экран приложения. Единственное место, где composition root связывает
 * фичи (INV-27): отсюда наружу не видны ни `Screen`, ни `ViewModel` фич.
 */
@Composable
public fun SyncClipApp() {
    var theme by remember { mutableStateOf(ThemeState()) }
    SyncClipTheme(theme = theme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AppLayout(
                theme = theme,
                onThemeChange = { updated -> theme = updated },
            )
        }
    }
}

@Composable
private fun AppLayout(
    theme: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
) {
    val navigator = remember { AppNavigator(HistoryRoute) }
    var section by remember { mutableStateOf(AppSection.HISTORY) }

    PlatformBackHandler(enabled = section == AppSection.HISTORY && navigator.canGoBack) {
        navigator.pop()
    }

    val onSelect: (AppSection) -> Unit = { selected ->
        section = selected
        navigator.reset(HistoryRoute)
    }

    val compact = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass ==
        WindowWidthSizeClass.COMPACT

    if (compact) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = { AppNavigationBar(selected = section, onSelect = onSelect) },
        ) { padding ->
            AppDestination(
                section = section,
                navigator = navigator,
                theme = theme,
                onThemeChange = onThemeChange,
                modifier = Modifier.padding(padding),
            )
        }
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            AppNavigationRail(selected = section, onSelect = onSelect)
            AppDestination(
                section = section,
                navigator = navigator,
                theme = theme,
                onThemeChange = onThemeChange,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AppDestination(
    section: AppSection,
    navigator: AppNavigator,
    theme: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (section) {
            AppSection.HISTORY -> {
                val route = navigator.current
                if (route is HistoryDetailRoute) {
                    historyDetailNavEntry(
                        route = route,
                        onBack = { navigator.pop() },
                    )()
                } else {
                    historyListNavEntry(
                        onOpenDetail = { eventId -> navigator.push(HistoryDetailRoute(eventId)) },
                    )()
                }
            }

            AppSection.SYNC -> syncNavEntry()()

            AppSection.SETTINGS -> settingsNavEntry(
                theme = theme,
                onThemeChange = onThemeChange,
            )()
        }
    }
}
