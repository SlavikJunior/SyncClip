package io.github.slavikjunior.syncclip.core.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Биометрическая блокировка приложения (US-022).
 *
 * Реализации: Android — `BiometricPrompt` (или подтверждение блокировки
 * экрана на API без биометрии), iOS — `LocalAuthentication`, Desktop —
 * [UnsupportedBiometricAuthenticator] (в JVM нет системного API).
 *
 * Платформа сама отслеживает уход в фон и возврат: общий экран только читает
 * [locked] и показывает форму разблокировки. Так lifecycle остаётся
 * платформенным, а `commonMain` не тянет Android-specific зависимости.
 */
public interface BiometricAuthenticator {

    /** Есть ли на устройстве настроенная биометрия. */
    public val isAvailable: Boolean

    /** `true`, когда приложение скрыто от пользователя до подтверждения. */
    public val locked: StateFlow<Boolean>

    /** Применяет настройку; [timeoutMinutes] = 0 — блокировать сразу при уходе в фон. */
    public fun configure(enabled: Boolean, timeoutMinutes: Int)

    /**
     * Показывает системный запрос.
     *
     * @return `true`, если пользователь подтвердил личность.
     */
    public suspend fun authenticate(title: String, subtitle: String, cancelLabel: String): Boolean

    /** Блокирует приложение немедленно (выход из сессии, смена настроек). */
    public fun lock()
}

/** Платформа без биометрии: блокировка не включается. */
public object UnsupportedBiometricAuthenticator : BiometricAuthenticator {

    private val neverLocked = MutableStateFlow(false)

    override val isAvailable: Boolean = false

    override val locked: StateFlow<Boolean> = neverLocked.asStateFlow()

    override fun configure(enabled: Boolean, timeoutMinutes: Int) = Unit

    override suspend fun authenticate(
        title: String,
        subtitle: String,
        cancelLabel: String,
    ): Boolean = false

    override fun lock() = Unit
}
