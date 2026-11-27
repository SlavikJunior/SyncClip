package io.github.slavikjunior.syncclip.core.di

import io.github.slavikjunior.syncclip.core.datastore.SessionRefreshOutcome
import io.github.slavikjunior.syncclip.core.datastore.SessionRefresher
import io.github.slavikjunior.syncclip.core.datastore.SessionSnapshot
import io.github.slavikjunior.syncclip.core.datastore.SessionState
import io.github.slavikjunior.syncclip.core.datastore.SessionStore
import io.github.slavikjunior.syncclip.shared.network.SyncClipHttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Проверка DI-обвязки refresh по `401`: клиент создаётся без цикла Koin,
 * адаптер `SessionRefresher` → `TokenRefresher` работает, а отвергнутый refresh
 * завершает сессию через `onSessionExpired`.
 */
internal class NetworkModuleRefreshTest {

    @Test
    fun `401 refreshes through the graph and retries with the rotated token`() = runTest {
        val session = InMemorySessionStore(accessToken = "old-access")
        val seen = mutableListOf<String?>()
        val engine = MockEngine { request ->
            seen += request.headers[HttpHeaders.Authorization]
            if (seen.size == 1) {
                respond("", HttpStatusCode.Unauthorized)
            } else {
                respond(
                    content = """{"devices":[]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }

        val app = koinApplication {
            modules(
                module {
                    single<HttpClientEngine>(named("httpEngine")) { engine }
                    single<SessionStore> { session }
                    single<SessionRefresher> {
                        object : SessionRefresher {
                            override suspend fun ensureFresh(): Boolean = true

                            override suspend fun refreshAfterUnauthorized(
                                staleAccessToken: String?,
                            ): SessionRefreshOutcome {
                                if (session.accessToken() != staleAccessToken) {
                                    return SessionRefreshOutcome.REFRESHED
                                }
                                session.updateTokens("new-access", "new-refresh")
                                return SessionRefreshOutcome.REFRESHED
                            }
                        }
                    }
                },
                networkModule(),
            )
        }

        try {
            val client = app.koin.get<SyncClipHttpClient>()
            val response = client.client.get("/v1/devices")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                listOf<String?>("Bearer old-access", "Bearer new-access"),
                seen.toList(),
                "повтор уходит с обновлённым токеном",
            )
            assertEquals("new-access", session.accessToken())
            client.close()
        } finally {
            app.close()
        }
    }

    @Test
    fun `rejected refresh signs the user out`() = runTest {
        val session = InMemorySessionStore(accessToken = "dead-access")
        val engine = MockEngine {
            respond("""{"error":"refresh_token_reused"}""", HttpStatusCode.Unauthorized)
        }

        val app = koinApplication {
            modules(
                module {
                    single<HttpClientEngine>(named("httpEngine")) { engine }
                    single<SessionStore> { session }
                    single<SessionRefresher> {
                        object : SessionRefresher {
                            override suspend fun ensureFresh(): Boolean = false
                            override suspend fun refreshAfterUnauthorized(
                                staleAccessToken: String?,
                            ): SessionRefreshOutcome = SessionRefreshOutcome.REJECTED
                        }
                    }
                },
                networkModule(),
            )
        }

        try {
            val client = app.koin.get<SyncClipHttpClient>()
            val response = client.client.get("/v1/devices")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertTrue(
                session.state.value is SessionState.SignedOut,
                "отвергнутый refresh должен разлогинить",
            )
            assertEquals(1, engine.requestHistory.size, "повторять нечего")
            client.close()
        } finally {
            app.close()
        }
    }
}

/** Сессия в памяти: тест проверяет обвязку, а не DataStore. */
private class InMemorySessionStore(accessToken: String) : SessionStore {

    private val mutableState = MutableStateFlow<SessionState>(
        SessionState.SignedIn(
            SessionSnapshot(
                userId = "user-1",
                accessToken = accessToken,
                refreshToken = "refresh-1",
            ),
        ),
    )

    override val state: StateFlow<SessionState> = mutableState

    override suspend fun refresh() = Unit

    override suspend fun save(session: SessionSnapshot) {
        mutableState.value = SessionState.SignedIn(session)
    }

    override suspend fun updateTokens(accessToken: String, refreshToken: String) {
        val current = (mutableState.value as? SessionState.SignedIn)?.session ?: return
        mutableState.value = SessionState.SignedIn(
            current.copy(accessToken = accessToken, refreshToken = refreshToken),
        )
    }

    override suspend fun clear() {
        mutableState.value = SessionState.SignedOut
    }

    override fun accessToken(): String? =
        (mutableState.value as? SessionState.SignedIn)?.session?.accessToken
}
