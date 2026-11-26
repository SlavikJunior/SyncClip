package io.github.slavikjunior.syncclip.core.datastore

/**
 * Состояние блокировки ввода мастер-пароля (FR-1.4).
 *
 * Клиент не дублирует серверную политику: он запоминает ответ сервера
 * (`remainingAttempts` или `Retry-After`) и не отправляет запросы, пока
 * блокировка активна. Состояние переживает перезапуск приложения.
 */
public data class MasterPasswordLockout(
    val remainingAttempts: Int = ATTEMPTS_PER_GROUP,
    val lockedUntilMillis: Long = 0L,
) {

    public fun isLocked(nowMillis: Long): Boolean = nowMillis < lockedUntilMillis

    /**
     * Секунды до конца блокировки с округлением вверх: сразу после ответа
     * сервера показываем ровно `05:00`, а не `04:59` из-за прошедших миллисекунд.
     */
    public fun remainingSeconds(nowMillis: Long): Long {
        val left = (lockedUntilMillis - nowMillis).coerceAtLeast(0)
        return (left + 999) / 1_000
    }

    public companion object {
        public const val ATTEMPTS_PER_GROUP: Int = 3
    }
}
