package io.github.slavikjunior.syncclip.app.common.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import io.github.slavikjunior.syncclip.app.common.ui.AppSection
import io.github.slavikjunior.syncclip.feature.auth.api.navigation.AuthRoute
import io.github.slavikjunior.syncclip.feature.history.api.navigation.HistoryDetailRoute
import io.github.slavikjunior.syncclip.feature.history.api.navigation.HistoryRoute
import io.github.slavikjunior.syncclip.feature.settings.api.navigation.SettingsRoute
import io.github.slavikjunior.syncclip.feature.sync.api.navigation.SyncStatusRoute
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Реестр ключей для `rememberNavBackStack` (INV-18).
 *
 * Восстановление стека после смерти процесса идёт через полиморфную
 * десериализацию `NavKey`, а открытый полиморфизм требует явной регистрации
 * подтипов. Новый маршрут обязан появиться здесь: незарегистрированный ключ
 * переживёт поворот экрана, но упадёт при восстановлении стека.
 *
 * `DeviceManagementRoute` не регистрируется: экрана для него ещё нет.
 */
internal val AppNavSavedState: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(HistoryRoute::class, HistoryRoute.serializer())
            subclass(HistoryDetailRoute::class, HistoryDetailRoute.serializer())
            subclass(SyncStatusRoute::class, SyncStatusRoute.serializer())
            subclass(SettingsRoute::class, SettingsRoute.serializer())
            subclass(AuthRoute::class, AuthRoute.serializer())
        }
    }
}

/** Корневой маршрут раздела: раздел и стек — одно состояние, а не два. */
internal fun AppSection.rootRoute(): NavKey = when (this) {
    AppSection.HISTORY -> HistoryRoute
    AppSection.SYNC -> SyncStatusRoute
    AppSection.SETTINGS -> SettingsRoute
}

/** Раздел, которому принадлежит ключ. */
internal fun sectionOf(key: NavKey): AppSection = when (key) {
    is HistoryRoute, is HistoryDetailRoute -> AppSection.HISTORY
    is SyncStatusRoute -> AppSection.SYNC
    is SettingsRoute -> AppSection.SETTINGS
    // Стек раздела содержит только его маршруты: иначе это ошибка сборки навигации,
    // а не повод молча показать другой экран (F6 аудита 2026-11-28).
    else -> error("Маршрут $key не принадлежит ни одному разделу")
}

/**
 * Переключение раздела: стек раздела начинается заново с его корня.
 *
 * Обе мутации выполняются в одном снапшоте, поэтому `NavDisplay` не увидит
 * промежуточный пустой стек.
 */
internal fun MutableList<NavKey>.resetTo(root: NavKey) {
    clear()
    add(root)
}

/**
 * Переход вперёд без дублей.
 *
 * Повторный тап по уже открытой карточке (двойной клик, дрожание пальца) не
 * должен добавлять второй экземпляр того же маршрута: иначе back требует двух
 * нажатий, а detail-панель пересоздаёт ViewModel (F14 аудита Nav3).
 */
internal fun MutableList<NavKey>.push(route: NavKey) {
    if (lastOrNull() != route) add(route)
}
