package io.github.slavikjunior.syncclip.core.datastore

/** Итог принудительного обновления пары после `401`. */
public enum class SessionRefreshOutcome {

    /** Пара обновлена (или уже обновлена параллельным запросом) — можно повторить запрос. */
    REFRESHED,

    /** Сервер отверг refresh-токен: сессия мертва, нужен повторный вход. */
    REJECTED,

    /** Сеть или `5xx`: сессию не трогаем, повторим позже. */
    UNAVAILABLE,
}

/**
 * Обновление пары токенов, когда access истёк.
 *
 * Реализация живёт в `:feature:auth:impl` (там HTTP-клиент сервера); транспорт и
 * composition root зависят только от этого контракта.
 */
public interface SessionRefresher {

    /**
     * Обновляет токены, если access истёк или истекает.
     *
     * @return `true`, если после вызова есть рабочий access-токен.
     */
    public suspend fun ensureFresh(): Boolean

    /**
     * Обновляет пару после ответа `401`, даже если `exp` ещё не наступил
     * (часы клиента и сервера могут расходиться).
     *
     * [staleAccessToken] — токен из отвергнутого запроса: если сессия уже
     * содержит другой access, обновление выполнил параллельный запрос и
     * повторный refresh не нужен (ротация отозвала бы семью).
     */
    public suspend fun refreshAfterUnauthorized(
        staleAccessToken: String?,
    ): SessionRefreshOutcome = if (ensureFresh()) {
        SessionRefreshOutcome.REFRESHED
    } else {
        SessionRefreshOutcome.UNAVAILABLE
    }
}
