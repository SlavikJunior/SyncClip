package io.github.slavikjunior.syncclip.core.di

import io.github.slavikjunior.syncclip.core.datastore.SessionRefreshOutcome
import io.github.slavikjunior.syncclip.core.datastore.SessionRefresher
import io.github.slavikjunior.syncclip.core.datastore.SessionStore
import io.github.slavikjunior.syncclip.core.datastore.publishSharedEndpoint
import io.github.slavikjunior.syncclip.shared.network.SyncClipHttpClient
import io.github.slavikjunior.syncclip.shared.network.TokenRefreshOutcome
import io.github.slavikjunior.syncclip.shared.network.TokenRefresher
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Адрес API по умолчанию: локальный сервер из README. */
private val defaultBaseUrl: String get() = defaultApiBaseUrl()

/**
 * Единственная точка создания HTTP-клиента (INV-24).
 *
 * Токен читается из [SessionStore] на каждый запрос: после входа клиент сразу
 * видит свежий access token без пересоздания `HttpClient`.
 *
 * [SessionRefresher] резолвится лениво: он сам зависит от `HttpClient`
 * (`POST /auth/refresh`), и жёсткая связь замкнула бы граф Koin.
 */
public fun networkModule(): Module = module {
    single<SyncClipHttpClient> {
        val resolver = this
        val session = getOrNull<SessionStore>()
        val baseUrl = getOrNull<String>(named("apiBaseUrl")) ?: defaultBaseUrl
        // iOS Share Extension шлёт пакет на тот же адрес, что и приложение.
        publishSharedEndpoint(baseUrl)
        SyncClipHttpClient(
            baseUrl = baseUrl,
            tokenProvider = { session?.accessToken() },
            tokenRefresher = TokenRefresher { staleAccessToken ->
                when (
                    resolver.getOrNull<SessionRefresher>()
                        ?.refreshAfterUnauthorized(staleAccessToken)
                ) {
                    SessionRefreshOutcome.REFRESHED -> TokenRefreshOutcome.REFRESHED
                    SessionRefreshOutcome.REJECTED -> TokenRefreshOutcome.REJECTED
                    SessionRefreshOutcome.UNAVAILABLE, null -> TokenRefreshOutcome.UNAVAILABLE
                }
            },
            onSessionExpired = { session?.clear() },
            // Тесты подменяют движок; в приложении — платформенный по умолчанию.
            engine = getOrNull<HttpClientEngine>(named("httpEngine")),
        )
    }
    single<HttpClient> { get<SyncClipHttpClient>().client }
}
