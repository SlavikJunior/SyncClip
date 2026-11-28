package io.github.slavikjunior.syncclip.shared.model

/**
 * Ключ маршрута в общем коде. Реализации @Serializable объявляются в `api` фич.
 *
 * Наследует маркер Navigation 3: `rememberNavBackStack` сохраняет стек только для
 * ключей этого типа, поэтому общего кода (INV-18) достаточно, чтобы back stack
 * переживал пересоздание процесса на всех платформах.
 */
public interface NavKey : androidx.navigation3.runtime.NavKey
