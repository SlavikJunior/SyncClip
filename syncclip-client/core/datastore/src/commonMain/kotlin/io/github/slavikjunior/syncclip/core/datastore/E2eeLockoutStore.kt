package io.github.slavikjunior.syncclip.core.datastore

import kotlinx.coroutines.flow.Flow

/** Хранилище состояния блокировки мастер-пароля (FR-1.4). */
public interface E2eeLockoutStore {

    public val state: Flow<MasterPasswordLockout>

    public suspend fun current(): MasterPasswordLockout

    /** Сервер посчитал попытку: осталось [remaining] до следующей блокировки. */
    public suspend fun recordAttempt(remaining: Int)

    /** Сервер заблокировал ввод до [lockedUntilMillis]. */
    public suspend fun lockUntil(lockedUntilMillis: Long)

    /** Успешный ввод сбрасывает счётчик и блокировку. */
    public suspend fun clear()
}
