package io.github.slavikjunior.syncclip.feature.history.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.slavikjunior.syncclip.feature.history.api.navigation.HistoryDetailRoute
import io.github.slavikjunior.syncclip.feature.history.api.navigation.HistoryRoute
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.ui.HistoryDetailScreen
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.ui.HistoryScreen

/**
 * Единственный публичный вход в фичу (INV-27). Точка входа приложения
 * связывает ключи [HistoryRoute]/[HistoryDetailRoute] с Navigation 3.
 */
public fun historyListNavEntry(
    onOpenDetail: (String) -> Unit,
): @Composable () -> Unit = {
    HistoryScreen(onOpenDetail = onOpenDetail, modifier = Modifier)
}

/** Детальный экран получает только ID из маршрута. */
public fun historyDetailNavEntry(
    route: HistoryDetailRoute,
    onBack: () -> Unit,
): @Composable () -> Unit = {
    HistoryDetailScreen(eventId = route.eventId, onBack = onBack, modifier = Modifier)
}
