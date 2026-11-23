package io.github.slavikjunior.syncclip.app.common.navigation

import androidx.compose.runtime.mutableStateListOf
import io.github.slavikjunior.syncclip.shared.model.NavKey

/**
 * Минимальный back stack приложения поверх [NavKey].
 *
 * Navigation 3 UI пока не публикуется для iOS, поэтому общий экранный слой
 * отображает ключи маршрутов сам (см. docs/PROFILE.md → INV-18). Ключи остаются
 * `@Serializable` и содержат только ID — переход на NavDisplay не затронет фичи.
 */
internal class AppNavigator(initial: NavKey) {

    private val entries = mutableStateListOf(initial)

    val current: NavKey
        get() = entries.last()

    val canGoBack: Boolean
        get() = entries.size > 1

    fun push(key: NavKey) {
        entries.add(key)
    }

    fun pop() {
        if (canGoBack) {
            entries.removeAt(entries.lastIndex)
        }
    }

    fun reset(key: NavKey) {
        entries.clear()
        entries.add(key)
    }
}
