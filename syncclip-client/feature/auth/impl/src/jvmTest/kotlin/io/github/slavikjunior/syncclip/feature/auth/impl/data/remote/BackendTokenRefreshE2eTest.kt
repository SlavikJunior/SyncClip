package io.github.slavikjunior.syncclip.feature.auth.impl.data.remote

import io.github.slavikjunior.syncclip.core.datastore.SessionRefreshOutcome
import io.github.slavikjunior.syncclip.core.datastore.SessionSnapshot
import io.github.slavikjunior.syncclip.core.datastore.SessionState
import io.github.slavikjunior.syncclip.core.datastore.SessionStore
import io.github.slavikjunior.syncclip.shared.network.SyncClipHttpClient
import io.github.slavikjunior.syncclip.shared.network.TokenRefreshOutcome
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/**
 * Авто-refresh на настоящем сервере: запрос с испорченным access-токеном
 * получает `401`, клиент ротирует пару через `POST /v1/auth/refresh` и
 * повторяет запрос уже с новым токеном.
 *
 * ```bash
 * SYNCCLIP_E2E_URL=http://localhost:18080 ./gradlew :feature:auth:impl:jvmTest \
 *   --tests '*BackendTokenRefreshE2eTest*'
 * ```
 */
internal class BackendTokenRefreshE2eTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `401 from the real server is refreshed and retried`() = runBlocking {
        val baseUrl = System.getenv("SYNCCLIP_E2E_URL") ?: return@runBlocking
        val account = register(baseUrl)

        val session = E2eSessionStore(
            accessToken = "expired-access-token",
            refreshToken = account.refreshToken,
        )
        var expiredCallbacks = 0
        lateinit var refresher: KtorSessionRefresher
        val http = SyncClipHttpClient(
            baseUrl = baseUrl,
            tokenProvider = { session.accessToken() },
            tokenRefresher = { stale ->
                refresher.refreshAfterUnauthorized(stale).toNetworkOutcome()
            },
            onSessionExpired = {
                expiredCallbacks++
                session.clear()
            },
        )
        refresher = KtorSessionRefresher(client = http.client, session = session)

        try {
            val response = http.client.get("/v1/devices")

            assertEquals(HttpStatusCode.OK, response.status, "после refresh запрос повторяется")
            assertNotEquals("expired-access-token", session.accessToken())
            assertEquals(0, expiredCallbacks, "сессия жива")
        } finally {
            http.close()
        }
    }

    @Test
    fun `rejected refresh from the real server signs the user out`() = runBlocking {
        val baseUrl = System.getenv("SYNCCLIP_E2E_URL") ?: return@runBlocking
        val account = register(baseUrl)

        val session = E2eSessionStore(
            accessToken = "expired-access-token",
            refreshToken = "not-a-refresh-token",
        )
        var expiredCallbacks = 0
        lateinit var refresher: KtorSessionRefresher
        val http = SyncClipHttpClient(
            baseUrl = baseUrl,
            tokenProvider = { session.accessToken() },
            tokenRefresher = { stale ->
                refresher.refreshAfterUnauthorized(stale).toNetworkOutcome()
            },
            onSessionExpired = {
                expiredCallbacks++
                session.clear()
            },
        )
        refresher = KtorSessionRefresher(client = http.client, session = session)

        try {
            val response = http.client.get("/v1/devices")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(1, expiredCallbacks)
            assertIs<SessionState.SignedOut>(session.state.value)
        } finally {
            http.close()
        }
    }

    private suspend fun register(baseUrl: String): AuthResponse {
        val bootstrap = HttpClient(CIO) {
            install(ContentNegotiation) { json(json) }
        }
        return try {
            bootstrap.post("$baseUrl/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(
                    AuthRequest(
                        email = "refresh-${Uuid.random()}@syncclip.local",
                        password = "refresh-e2e-password",
                        deviceId = Uuid.random().toString(),
                        deviceName = "Refresh e2e",
                        osType = "Linux",
                    ),
                )
            }.body()
        } finally {
            bootstrap.close()
        }
    }

    @Serializable
    private data class AuthRequest(
        val email: String,
        val password: String,
        val deviceId: String,
        val deviceName: String,
        val osType: String,
    )

    @Serializable
    private data class AuthResponse(
        val userId: String,
        val accessToken: String,
        val refreshToken: String,
    )
}

/** `:core:datastore` не знает про HTTP-клиент: адаптер живёт на границе. */
private fun SessionRefreshOutcome.toNetworkOutcome(): TokenRefreshOutcome = when (this) {
    SessionRefreshOutcome.REFRESHED -> TokenRefreshOutcome.REFRESHED
    SessionRefreshOutcome.REJECTED -> TokenRefreshOutcome.REJECTED
    SessionRefreshOutcome.UNAVAILABLE -> TokenRefreshOutcome.UNAVAILABLE
}

/** Сессия в памяти с сохранением ротации, как у боевого DataStore. */
private class E2eSessionStore(
    accessToken: String,
    refreshToken: String,
) : SessionStore {

    private val mutableState = MutableStateFlow<SessionState>(
        SessionState.SignedIn(
            SessionSnapshot(
                userId = "e2e-user",
                accessToken = accessToken,
                refreshToken = refreshToken,
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
