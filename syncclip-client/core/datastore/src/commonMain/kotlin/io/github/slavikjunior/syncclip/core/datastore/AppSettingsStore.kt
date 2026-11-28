package io.github.slavikjunior.syncclip.core.datastore

import kotlinx.coroutines.flow.Flow

/**
 * Настройки интерфейса, уведомлений и приватности (Phase 2).
 *
 * Все значения локальные и не синхронизируются между устройствами: тема и
 * размер шрифта зависят от экрана конкретного устройства (US-009, US-011).
 */
public interface AppSettingsStore {

    public val state: Flow<AppSettings>

    public suspend fun current(): AppSettings

    /** Сохраняет режим и палитру темы; неизвестные значения отвергаются. */
    public suspend fun setTheme(mode: String, preset: String): Boolean

    /** Новый seed палитры «Random» (US-015). */
    public suspend fun setRandomThemeSeed(seed: Long)

    /** US-011: один из четырёх шагов. @return `false`, если значение вне списка. */
    public suspend fun setFontScale(scale: Float): Boolean

    public suspend fun setNotifications(settings: NotificationSettings)

    /** US-021. */
    public suspend fun setAutoClearSensitive(enabled: Boolean)

    /** US-022; при [timeoutMinutes] вне диапазона возвращает `false`. */
    public suspend fun setBiometricLock(enabled: Boolean, timeoutMinutes: Int): Boolean
}
