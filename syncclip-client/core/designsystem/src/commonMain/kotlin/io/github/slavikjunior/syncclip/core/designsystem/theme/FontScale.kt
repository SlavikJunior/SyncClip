package io.github.slavikjunior.syncclip.core.designsystem.theme

/**
 * Масштаб типографики (US-011).
 *
 * Четыре шага из спецификации: 0.85 / 1.0 / 1.15 / 1.3. Хранится в настройках
 * множителем, а экран показывает название шага.
 */
public enum class FontScale(public val multiplier: Float) {
    SMALL(0.85f),
    MEDIUM(1.0f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f),
    ;

    public companion object {

        /** Ближайший известный шаг: невалидное значение настроек не ломает экран. */
        public fun fromMultiplier(multiplier: Float): FontScale =
            entries.minBy { entry -> kotlin.math.abs(entry.multiplier - multiplier) }
                    .takeIf { entry -> kotlin.math.abs(entry.multiplier - multiplier) < 0.08f }
                ?: MEDIUM
    }
}
