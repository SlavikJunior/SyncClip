package io.github.slavikjunior.syncclip.shared.network

/**
 * Итог обновления пары токенов после `401` (FR-1.3, auth-поток).
 *
 * Различаем «сессия мертва» и «сеть недоступна»: разлогинивать пользователя
 * из-за таймаута нельзя, а повторять запрос с отвергнутым refresh-токеном
 * бессмысленно.
 */
public enum class TokenRefreshOutcome {

    /** Пара обновлена (или уже обновлена параллельным запросом) — запрос можно повторить. */
    REFRESHED,

    /** Сервер отверг refresh-токен: нужен повторный вход. */
    REJECTED,

    /** Сеть или `5xx`: сессию не трогаем, ошибку отдаём вызывающему. */
    UNAVAILABLE,
}

/**
 * Обновление access-токена по `401`.
 *
 * Контракт живёт в `:shared:network`, чтобы HTTP-клиент не зависел от
 * `:core:datastore`; реализация (DataStore + `POST /v1/auth/refresh`) —
 * в `:feature:auth:impl`, адаптер — в `:core:di`.
 */
public fun interface TokenRefresher {

    /**
     * @param staleAccessToken токен из заголовка запроса, получившего `401`;
     *   `null`, если запрос шёл без авторизации.
     */
    public suspend fun refresh(staleAccessToken: String?): TokenRefreshOutcome
}
