package io.github.slavikjunior.syncclip.feature.history.impl.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import io.github.slavikjunior.syncclip.core.designsystem.adaptive.isWideWindow
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryDetailEffect
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryDetailIntent
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryDetailViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun HistoryDetailScreen(
    eventId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // eventId передаётся в фабрику Koin, чтобы состояние переживало рекомпозицию (INV-08, INV-22).
    val viewModel: HistoryDetailViewModel = koinViewModel { parametersOf(eventId) }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.handleIntent(HistoryDetailIntent.Load)
        viewModel.effect.collect { effect ->
            when (effect) {
                HistoryDetailEffect.NavigateBack -> onBack()
            }
        }
    }

    HistoryDetailContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::handleIntent,
        onBack = onBack,
        // В двухпанельном режиме деталь — правая панель списка: back-стрелка
        // там не нужна, выход из сцены делает жест/системная кнопка.
        showBackArrow = !currentWindowAdaptiveInfoV2().windowSizeClass.isWideWindow(),
    )
}
