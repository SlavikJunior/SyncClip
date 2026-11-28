package io.github.slavikjunior.syncclip.feature.history.impl

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.slavikjunior.syncclip.feature.history.api.navigation.HistoryDetailRoute
import io.github.slavikjunior.syncclip.feature.history.api.navigation.HistoryRoute
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.ui.HistoryDetailPlaceholder
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.ui.HistoryDetailScreen
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.ui.HistoryScreen

/**
 * Единственный публичный вход в фичу (INV-27): регистрирует экраны истории
 * в `entryProvider` приложения и не отдаёт наружу `Screen`/`ViewModel`.
 *
 * Двухпанельный режим собирает `ListDetailSceneStrategy` (адаптивный шаг 3):
 * список помечен как list-pane с заглушкой на месте детали, деталь — как
 * detail-pane. На узком окне сцена показывает только верхнюю запись стека,
 * поэтому отдельного «режима двух панелей» в экранах больше нет.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
public fun EntryProviderScope<NavKey>.historyNavEntry(
    onOpenDetail: (String) -> Unit,
    onBack: () -> Unit,
) {
    entry<HistoryRoute>(
        metadata = ListDetailSceneStrategy.listPane(
            detailPlaceholder = { HistoryDetailPlaceholder() },
        ),
    ) {
        HistoryScreen(onOpenDetail = onOpenDetail, modifier = Modifier)
    }
    // Детальный экран получает только ID из маршрута: модель загружается в получателе.
    entry<HistoryDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        HistoryDetailScreen(eventId = route.eventId, onBack = onBack, modifier = Modifier)
    }
}
