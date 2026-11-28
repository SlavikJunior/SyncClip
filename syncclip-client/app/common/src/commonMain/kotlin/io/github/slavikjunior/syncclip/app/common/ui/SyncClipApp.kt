package io.github.slavikjunior.syncclip.app.common.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.window.core.layout.WindowWidthSizeClass
import io.github.slavikjunior.syncclip.app.common.navigation.AppNavigator
import io.github.slavikjunior.syncclip.app.common.runtime.AppSectionRequests
import io.github.slavikjunior.syncclip.app.common.runtime.SyncRuntime
import io.github.slavikjunior.syncclip.app.common.ui.e2ee.E2eeUnlockScreen
import io.github.slavikjunior.syncclip.app.common.ui.platform.PlatformBackHandler
import io.github.slavikjunior.syncclip.core.datastore.E2eeKeyStore
import io.github.slavikjunior.syncclip.core.datastore.E2eeLockoutStore
import io.github.slavikjunior.syncclip.core.datastore.SessionState
import io.github.slavikjunior.syncclip.core.datastore.SessionStore
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeState
import io.github.slavikjunior.syncclip.core.firebase.PushTokenSource
import io.github.slavikjunior.syncclip.feature.auth.api.PushTokenRegistrar
import io.github.slavikjunior.syncclip.feature.auth.impl.authNavEntry
import io.github.slavikjunior.syncclip.feature.history.api.navigation.HistoryDetailRoute
import io.github.slavikjunior.syncclip.feature.history.api.navigation.HistoryRoute
import io.github.slavikjunior.syncclip.feature.history.impl.historyDetailNavEntry
import io.github.slavikjunior.syncclip.feature.history.impl.historyListNavEntry
import io.github.slavikjunior.syncclip.feature.settings.impl.settingsNavEntry
import io.github.slavikjunior.syncclip.feature.sync.api.E2eeKeyProvisioner
import io.github.slavikjunior.syncclip.feature.sync.impl.syncNavEntry
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Корневой экран приложения. Единственное место, где composition root связывает
 * фичи (INV-27): отсюда наружу не видны ни `Screen`, ни `ViewModel` фич.
 *
 * Сессия — единственный источник правды для входа: пока `SessionState.Loading`,
 * показывается прогресс; `SignedOut` открывает Firebase-форму входа;
 * `SignedIn` — основной каркас приложения.
 */
@Composable
public fun SyncClipApp() {
    var theme by remember { mutableStateOf(ThemeState()) }
    val sessionStore: SessionStore = koinInject()
    val pushTokens: PushTokenSource = koinInject()
    val pushRegistrar: PushTokenRegistrar = koinInject()
    val runtime: SyncRuntime = koinInject()
    val sectionRequests: AppSectionRequests = koinInject()
    val keyProvisioner: E2eeKeyProvisioner = koinInject()
    val lockoutStore: E2eeLockoutStore = koinInject()
    val keyStore: E2eeKeyStore = koinInject()
    val scope = rememberCoroutineScope()
    val sessionState by sessionStore.state.collectAsState()
    val e2eeKey by keyStore.keyState.collectAsState()
    var keyHydrated by remember { mutableStateOf(false) }

    LaunchedEffect(sessionStore) {
        sessionStore.refresh()
    }

    // Ключ читается из Keystore/Keychain один раз за процесс: до этого нельзя
    // решать, показывать мастер-пароль или сразу основной экран.
    LaunchedEffect(Unit) {
        keyStore.hydrate()
        keyHydrated = true
    }

    // Конвейер живёт вне Compose (на Desktop окно можно скрыть в трей).
    // Desktop `main` запускает его раньше; повторный старт идемпотентен.
    LaunchedEffect(Unit) {
        runtime.start(this)
    }

    // Ключ выводится из мастер-пароля на сессию; при выходе его нельзя оставлять.
    LaunchedEffect(sessionState) {
        if (sessionState is SessionState.SignedOut) {
            keyStore.clear()
        }
    }

    // FCM-токен ротируется независимо от входа: пока есть сессия, сервер должен
    // знать актуальный токен устройства (FR-1.5, §11.1).
    LaunchedEffect(sessionState) {
        if (sessionState is SessionState.SignedIn) {
            // Регистрация push — best-effort: сбой DataStore/IPC при чтении
            // идентичности не должен ронять приложение сразу после входа.
            runCatching { pushRegistrar.registerCurrentToken() }
            pushTokens.tokens
                // Сбой платформенного источника токенов не должен ронять
                // композицию: push — вспомогательный канал доставки.
                .catch { }
                .collect { runCatching { pushRegistrar.registerCurrentToken() } }
        }
    }

    SyncClipTheme(theme = theme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (sessionState) {
                SessionState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                SessionState.SignedOut -> authNavEntry(
                    onAuthenticated = { scope.launch { sessionStore.refresh() } },
                )()

                is SessionState.SignedIn -> when {
                    // Keystore/Keychain читается асинхронно — не мигаем формой пароля.
                    !keyHydrated -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    e2eeKey == null -> E2eeUnlockScreen(
                        provisioner = keyProvisioner,
                        lockoutStore = lockoutStore,
                        onUnlocked = { key -> scope.launch { keyStore.setKey(key) } },
                    )

                    else -> AppLayout(
                        theme = theme,
                        onThemeChange = { updated -> theme = updated },
                        sectionRequests = sectionRequests,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppLayout(
    theme: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    sectionRequests: AppSectionRequests,
) {
    val navigator = remember { AppNavigator(HistoryRoute) }
    var section by remember { mutableStateOf(AppSection.HISTORY) }

    // Трей/хоткей просят открыть историю, не зная о навигации.
    LaunchedEffect(sectionRequests) {
        sectionRequests.history.collect {
            section = AppSection.HISTORY
            navigator.reset(HistoryRoute)
        }
    }

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
