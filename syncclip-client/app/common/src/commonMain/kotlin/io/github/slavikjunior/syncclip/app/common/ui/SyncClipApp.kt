package io.github.slavikjunior.syncclip.app.common.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.slavikjunior.syncclip.app.common.navigation.AppNavSavedState
import io.github.slavikjunior.syncclip.app.common.navigation.push
import io.github.slavikjunior.syncclip.app.common.navigation.resetTo
import io.github.slavikjunior.syncclip.app.common.navigation.rootRoute
import io.github.slavikjunior.syncclip.app.common.runtime.AppSectionRequests
import io.github.slavikjunior.syncclip.app.common.runtime.SyncRuntime
import io.github.slavikjunior.syncclip.app.common.ui.e2ee.E2eeUnlockScreen
import io.github.slavikjunior.syncclip.app.common.ui.lock.BiometricLockScreen
import io.github.slavikjunior.syncclip.core.datastore.AppSettings
import io.github.slavikjunior.syncclip.core.datastore.AppSettingsStore
import io.github.slavikjunior.syncclip.core.datastore.E2eeKeyStore
import io.github.slavikjunior.syncclip.core.datastore.E2eeLockoutStore
import io.github.slavikjunior.syncclip.core.datastore.SessionState
import io.github.slavikjunior.syncclip.core.datastore.SessionStore
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipNavigationItem
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipNavigationScaffold
import io.github.slavikjunior.syncclip.core.designsystem.theme.FontScale
import io.github.slavikjunior.syncclip.core.designsystem.theme.RandomTheme
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemePreset
import io.github.slavikjunior.syncclip.core.designsystem.theme.ThemeState
import io.github.slavikjunior.syncclip.core.firebase.PushTokenSource
import io.github.slavikjunior.syncclip.core.platform.BiometricAuthenticator
import io.github.slavikjunior.syncclip.feature.auth.api.PushTokenRegistrar
import io.github.slavikjunior.syncclip.feature.auth.api.navigation.AuthRoute
import io.github.slavikjunior.syncclip.feature.auth.impl.authNavEntry
import io.github.slavikjunior.syncclip.feature.history.api.navigation.HistoryDetailRoute
import io.github.slavikjunior.syncclip.feature.history.impl.historyNavEntry
import io.github.slavikjunior.syncclip.feature.settings.impl.settingsNavEntry
import io.github.slavikjunior.syncclip.feature.sync.api.E2eeKeyProvisioner
import io.github.slavikjunior.syncclip.feature.sync.impl.syncNavEntry
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Корневой экран приложения. Единственное место, где composition root связывает
 * фичи (INV-27): отсюда наружу не видны ни `Screen`, ни `ViewModel` фич.
 *
 * Сессия — единственный источник правды для входа: пока `SessionState.Loading`,
 * показывается прогресс; `SignedOut` открывает Firebase-форму входа;
 * `SignedIn` — основной каркас приложения.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
public fun SyncClipApp() {
    var theme by remember { mutableStateOf(ThemeState()) }
    var fontScale by remember { mutableStateOf(FontScale.MEDIUM) }
    val appSettingsStore: AppSettingsStore = koinInject()
    val biometrics: BiometricAuthenticator = koinInject()
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
    val locked by biometrics.locked.collectAsState()
    var keyHydrated by remember { mutableStateOf(false) }

    LaunchedEffect(sessionStore) {
        sessionStore.refresh()
    }

    // Тема и размер шрифта — из DataStore (US-011, US-012): после перезапуска
    // выбор пользователя должен сохраниться, а не сбрасываться в Aqua.
    LaunchedEffect(appSettingsStore) {
        val settings = runCatching { appSettingsStore.current() }.getOrNull() ?: AppSettings()
        theme = settings.appearance.toThemeState()
        fontScale = settings.appearance.toFontScale()
        // Биометрия настраивается до первого показа контента: платформа иначе
        // не знает, блокировать ли приложение при возврате из фона.
        runCatching {
            biometrics.configure(
                enabled = settings.security.biometricLock,
                timeoutMinutes = settings.security.timeoutMinutes,
            )
        }
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

    // Тема и шрифт сохраняются на каждое изменение: экран настроек живёт
    // недолго, а выбор должен пережить перезапуск процесса.
    val onThemeChange: (ThemeState) -> Unit = { updated ->
        theme = updated
        scope.launch {
            runCatching { appSettingsStore.setTheme(updated.mode.name, updated.preset.name) }
        }
    }
    val onRegenerateRandomTheme: () -> Unit = {
        val seed = RandomTheme.newSeed()
        theme = theme.copy(preset = ThemePreset.RANDOM, randomSeed = seed)
        scope.launch {
            runCatching {
                appSettingsStore.setTheme(theme.mode.name, ThemePreset.RANDOM.name)
                appSettingsStore.setRandomThemeSeed(seed)
            }
        }
    }
    val onFontScaleChange: (FontScale) -> Unit = { scale ->
        fontScale = scale
        scope.launch { runCatching { appSettingsStore.setFontScale(scale.multiplier) } }
    }

    SyncClipTheme(theme = theme, fontScale = fontScale.multiplier) {
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

                SessionState.SignedOut -> AuthFlow(
                    onAuthenticated = { scope.launch { sessionStore.refresh() } },
                )

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

                    else -> if (locked) {
                        // US-022: приложение скрыто до подтверждения личности.
                        // Ключ E2EE при этом уже разблокирован — экран не
                        // дублирует мастер-пароль.
                        BiometricLockScreen(authenticator = biometrics)
                    } else {
                        AppLayout(
                            theme = theme,
                            fontScale = fontScale,
                            onThemeChange = onThemeChange,
                            onRegenerateRandomTheme = onRegenerateRandomTheme,
                            onFontScaleChange = onFontScaleChange,
                            sectionRequests = sectionRequests,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun AppLayout(
    theme: ThemeState,
    fontScale: FontScale,
    onThemeChange: (ThemeState) -> Unit,
    onRegenerateRandomTheme: () -> Unit,
    onFontScaleChange: (FontScale) -> Unit,
    sectionRequests: AppSectionRequests,
) {
    // Стек — единственный источник правды о текущем экране (INV-18), а у
    // каждого верхнеуровневого раздела он свой (рецепт Nav3 «multiple back
    // stacks»): переключение вкладки не сбрасывает историю раздела, возврат
    // показывает его на прежнем экране. `rememberNavBackStack` восстанавливает
    // все три стека после смерти процесса на всех платформах.
    var sectionName by rememberSaveable { mutableStateOf(AppSection.HISTORY.name) }
    val section = AppSection.valueOf(sectionName)

    val historyStack = rememberNavBackStack(AppNavSavedState, AppSection.HISTORY.rootRoute())
    val syncStack = rememberNavBackStack(AppNavSavedState, AppSection.SYNC.rootRoute())
    val settingsStack = rememberNavBackStack(AppNavSavedState, AppSection.SETTINGS.rootRoute())
    val backStack = when (section) {
        AppSection.HISTORY -> historyStack
        AppSection.SYNC -> syncStack
        AppSection.SETTINGS -> settingsStack
    }

    // Трей/хоткей просят открыть историю, не зная о навигации.
    LaunchedEffect(sectionRequests) {
        sectionRequests.history.collect {
            sectionName = AppSection.HISTORY.name
            historyStack.resetTo(AppSection.HISTORY.rootRoute())
        }
    }

    // Панель/rail выбирает сам каркас по ширине окна (адаптивный шаг 2).
    SyncClipNavigationScaffold(
        items = AppSection.entries.map { entry ->
            SyncClipNavigationItem(
                icon = entry.icon,
                label = stringResource(entry.label),
            )
        },
        selectedIndex = AppSection.entries.indexOf(section),
        // Стеки не сбрасываются: повторный выбор раздела возвращает его
        // последний экран (F5 аудита Nav3).
        onSelect = { index -> sectionName = AppSection.entries[index].name },
    ) { padding ->
        // Адаптивный шаг 3: list-detail раскладывает сцена Nav3, а не экран.
        // На COMPACT она отдаёт стек обычному NavDisplay (деталь — отдельный
        // экран), на MEDIUM/EXPANDED показывает список и деталь двумя панелями
        // и сама снимает detail-запись со стека при возврате.
        val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(padding),
            // На Android системный back обрабатывает сам NavDisplay
            // (включая predictive back); на корне он отдаёт событие платформе.
            onBack = { backStack.removeLastOrNull() },
            sceneStrategies = listOf(listDetailStrategy),
            entryProvider = entryProvider<NavKey> {
                historyNavEntry(
                    // push вместо add: повторный тап/двойной клик не создаёт
                    // второй экземпляр того же маршрута (F14 аудита Nav3).
                    onOpenDetail = { eventId -> historyStack.push(HistoryDetailRoute(eventId)) },
                    onBack = { historyStack.removeLastOrNull() },
                )
                syncNavEntry()
                settingsNavEntry(
                    theme = theme,
                    fontScale = fontScale,
                    onThemeChange = onThemeChange,
                    onRegenerateRandomTheme = onRegenerateRandomTheme,
                    onFontScaleChange = onFontScaleChange,
                )
            },
        )
    }
}

/** Вход — тоже маршрут Nav3: один экран, но стеком владеет NavDisplay. */
@Composable
private fun AuthFlow(onAuthenticated: () -> Unit) {
    val backStack = rememberNavBackStack(AppNavSavedState, AuthRoute)
    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider<NavKey> {
            authNavEntry(onAuthenticated = onAuthenticated)
        },
    )
}
