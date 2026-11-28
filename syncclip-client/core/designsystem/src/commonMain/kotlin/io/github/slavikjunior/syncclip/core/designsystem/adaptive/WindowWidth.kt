package io.github.slavikjunior.syncclip.core.designsystem.adaptive

import androidx.window.core.layout.WindowSizeClass

/**
 * Единственный источник правды о «широком» окне (адаптивный шаг 2, US-003).
 *
 * Порог — MEDIUM Material 3 (600 dp): от него приложение переходит к боковой
 * навигации, двухпанельному list-detail и ограничению ширины контента.
 * Раньше это выражение дублировалось в каркасе навигации и в экране истории,
 * из-за чего пороги могли разойтись.
 *
 * Вызывается от `currentWindowAdaptiveInfoV2().windowSizeClass` — библиотечной
 * функции, поэтому сама по себе остаётся обычной (не composable) проверкой.
 */
public fun WindowSizeClass.isWideWindow(): Boolean =
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
