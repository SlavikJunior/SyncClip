package io.github.slavikjunior.syncclip.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing

/**
 * Тайминги и кривые анимаций SyncClip.
 *
 * Держим их здесь, чтобы переходы в разных фичах ощущались одинаково.
 * Экраны обязаны учитывать reduced motion (см. LocalReducedMotion).
 */
public object SyncClipMotion {
    /** Мгновенная реакция: нажатие, подсветка, ripple. */
    public const val Fast: Int = 120

    /** Стандартный переход: появление карточек, смена состояния. */
    public const val Standard: Int = 200

    /** Крупные переходы: экран, two-pane, shared element. */
    public const val Slow: Int = 320

    /** Период пульсации статуса онлайн-устройства. */
    public const val SyncPulse: Int = 1200

    public val StandardEasing: Easing = FastOutSlowInEasing
    public val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
