package io.github.slavikjunior.syncclip.feature.sync.api

/** Результат разблокировки E2EE мастер-паролем. */
public sealed interface E2eeUnlockResult {

    /** Пароль верный, ключ выведен из `PBKDF2(masterPassword, salt=userId)`. */
    public data class Success(val key: ByteArray) : E2eeUnlockResult

    /**
     * Canary сервера не расшифровался.
     *
     * @param remainingAttempts сколько попыток осталось до блокировки; `null`,
     *   если сервер недоступен и ответ неизвестен.
     */
    public data class InvalidPassword(
        val remainingAttempts: Int? = null,
    ) : E2eeUnlockResult

    /** Сервер заблокировал ввод на [retryAfterSeconds] секунд (FR-1.4). */
    public data class Locked(val retryAfterSeconds: Long) : E2eeUnlockResult

    /** Сеть/сессия недоступны — пароль не проверен, пробуем ещё раз позже. */
    public data class Unavailable(val message: String) : E2eeUnlockResult
}
