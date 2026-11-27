package io.github.slavikjunior.syncclip.feature.history.impl

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.slavikjunior.syncclip.core.database.ClipboardEventDao
import io.github.slavikjunior.syncclip.feature.history.impl.data.repository.FakeClipboardEventDao
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryDetailViewModel
import io.github.slavikjunior.syncclip.feature.history.impl.presentation.state.HistoryViewModel
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import kotlin.test.assertNotNull

/**
 * INV-08: ViewModel должен приходить из контейнера через `koinViewModel()`,
 * то есть жить в `ViewModelStore`, а не в `remember`.
 *
 * Тест закрывает две вещи, которые не видит компилятор: наличие
 * `ViewModelStoreOwner` в composition (на Desktop/iOS его даёт Compose
 * UI, на Android — Activity) и совпадение параметров Koin-фабрики.
 */
@OptIn(ExperimentalTestApi::class)
internal class HistoryViewModelWiringTest {

    @Before
    fun setUp() {
        startKoin {
            modules(
                historyModule,
                module { single<ClipboardEventDao> { FakeClipboardEventDao() } },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `ViewModel создаётся контейнером и получает owner из Composition`() = runDesktopComposeUiTest {
        var resolved: HistoryViewModel? = null
        setContent {
            resolved = koinViewModel<HistoryViewModel>()
        }
        waitForIdle()
        assertNotNull(resolved, "koinViewModel() не вернул ViewModel: нет owner'а или фабрики")
    }

    @Test
    fun `ViewModel деталей получает eventId параметром фабрики`() = runDesktopComposeUiTest {
        var resolved: HistoryDetailViewModel? = null
        setContent {
            resolved = koinViewModel<HistoryDetailViewModel> { parametersOf("event-1") }
        }
        waitForIdle()
        assertNotNull(resolved, "фабрика с parametersOf не сработала")
    }
}
