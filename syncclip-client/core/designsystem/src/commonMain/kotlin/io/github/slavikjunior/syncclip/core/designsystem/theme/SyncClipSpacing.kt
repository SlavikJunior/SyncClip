package io.github.slavikjunior.syncclip.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Шкала отступов SyncClip: шаг 4 dp, экранный отступ 20 dp.
 *
 * Используется вместо магических чисел в экранах и компонентах.
 */
public object SyncClipSpacing {
    public val xxs: Dp = 2.dp
    public val xs: Dp = 4.dp
    public val sm: Dp = 8.dp
    public val md: Dp = 12.dp
    public val lg: Dp = 16.dp
    public val xl: Dp = 24.dp
    public val xxl: Dp = 32.dp

    /** Горизонтальный отступ контента на телефоне. */
    public val screen: Dp = 20.dp

    /** Минимальная зона нажатия по гайдлайнам доступности. */
    public val minTouchTarget: Dp = 48.dp

    /**
     * Предел ширины одноколоночного контента (настройки, синхронизация).
     *
     * 840 dp — верхняя граница MEDIUM-диапазона: дальше строки становятся
     * нечитаемыми, поэтому контент центрируется, а не растягивается.
     */
    public val contentMaxWidth: Dp = 840.dp

    /** Предел ширины формы (мастер-пароль): уже, чем контент, ради читаемости. */
    public val formMaxWidth: Dp = 520.dp
}
