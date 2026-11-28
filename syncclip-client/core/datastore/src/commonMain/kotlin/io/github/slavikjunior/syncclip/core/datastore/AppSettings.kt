package io.github.slavikjunior.syncclip.core.datastore

/**
 * Локальные настройки интерфейса и приватности (Phase 2).
 *
 * Хранятся отдельно от [ClipboardSettings]: те читает конвейер захвата в момент
 * события, а эти — экран настроек и композиция темы. Изменение шрифта или темы
 * не должно трогать лимиты истории.
 *
 * Строковые ключи темы намеренно не типизированы enum'ами `:core:designsystem`:
 * хранилище — инфраструктура и не знает про UI-модуль. Отображение ключа в
 * палитру делает composition root, неизвестное значение трактуется как default.
 */
public data class AppSettings(
    val appearance: AppearanceSettings = AppearanceSettings(),
    val notifications: NotificationSettings = NotificationSettings(),
    /** US-021: автоудаление записей, похожих на пароли и токены. */
    val autoClearSensitive: Boolean = false,
    val security: SecuritySettings = SecuritySettings(),
)

/** Внешний вид: режим, палитра, seed случайной темы, масштаб шрифта. */
public data class AppearanceSettings(
    val themeMode: String = "SYSTEM",
    val themePreset: String = "AQUA",
    /** Seed палитры «Random» (US-015); `null` — тема ещё не генерировалась. */
    val randomThemeSeed: Long? = null,
    /** Множитель типографики (US-011): 0.85 / 1.0 / 1.15 / 1.3. */
    val fontScale: Float = DEFAULT_FONT_SCALE,
) {
    public companion object {
        public const val DEFAULT_FONT_SCALE: Float = 1.0f
    }
}

/** Настройки уведомлений (US-009). Значения по умолчанию — из спецификации. */
public data class NotificationSettings(
    /** Уведомлять о копии с другого устройства. */
    val newCopies: Boolean = false,
    /** Уведомлять о подключении нового устройства. */
    val newDevice: Boolean = true,
    /** Уведомлять о неудачной попытке мастер-пароля. */
    val failedSignIn: Boolean = true,
    val sound: Boolean = true,
    val vibration: Boolean = true,
)

/**
 * Биометрическая блокировка (US-022).
 *
 * [timeoutMinutes] = 0 — блокировать сразу при уходе приложения в фон.
 */
public data class SecuritySettings(
    val biometricLock: Boolean = false,
    val timeoutMinutes: Int = DEFAULT_TIMEOUT_MINUTES,
) {
    public companion object {
        public const val DEFAULT_TIMEOUT_MINUTES: Int = 5
    }
}
