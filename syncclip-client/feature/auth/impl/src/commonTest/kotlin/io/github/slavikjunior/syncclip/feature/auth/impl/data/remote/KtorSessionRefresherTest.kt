package io.github.slavikjunior.syncclip.feature.auth.impl.data.remote

import io.github.slavikjunior.syncclip.core.datastore.SessionRefreshOutcome
import io.github.slavikjunior.syncclip.core.datastore.SessionSnapshot
import io.github.slavikjunior.syncclip.core.datastore.SessionState
import io.github.slavikjunior.syncclip.core.datastore.SessionStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Refresh-флоу: сервер ротирует пару, клиент обязан сохранить оба токена.
 *
 * Время фиксируется через `clock`, поэтому тест не зависит от реального JWT.
 */
internal class KtorSessionRefresherTest {

    // {"exp":1000} — истекает в 1_000_000 мс.
    private val expiredToken = "eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjEwMDB9.sig"
    // {"exp":2000000000} — истекает в 2_000_000_000_000 мс.
    private val freshToken = "eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjIwMDAwMDAwMDAsInN1YiI6InVzZXIifQ.sig"

    @Test
    fun `expired token is rotated and both parts are stored`() = runTest {
        val store = StubSessionStore(expiredToken)
        var postedBody = ""
        val engine = MockEngine { request ->
            assertEquals("/v1/auth/refresh", request.url.encodedPath)
            postedBody = (request.body as io.ktor.http.content.TextContent).text
            respond(
                content = """{"accessToken":"new-access","refreshToken":"new-refresh"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val refreshed = refresher(engine, store).ensureFresh()

        assertTrue(refreshed)
        assertTrue(postedBody.contains("old-refresh"), "на сервер уходит текущий refresh")
        assertEquals("new-access", store.snapshot?.accessToken)
        assertEquals("new-refresh", store.snapshot?.refreshToken)
    }

    @Test
    fun `fresh token does not hit the network`() = runTest {
        val store = StubSessionStore(freshToken)
        val engine = MockEngine { error("refresh не должен вызываться") }

        assertTrue(refresher(engine, store).ensureFresh())
        assertEquals(freshToken, store.snapshot?.accessToken)
    }

    @Test
    fun `rejected refresh keeps the old session`() = runTest {
        val store = StubSessionStore(expiredToken)
        val engine = MockEngine {
            respond("""{"error":"token_expired"}""", HttpStatusCode.Unauthorized)
        }

        assertFalse(refresher(engine, store).ensureFresh())
        assertEquals(expiredToken, store.snapshot?.accessToken)
    }

    @Test
    fun `401 rotates the pair even when exp still looks fresh`() = runTest {
        val store = StubSessionStore(freshToken)
        var posts = 0
        val engine = MockEngine {
            posts++
            respond(
                content = """{"accessToken":"new-access","refreshToken":"new-refresh"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val outcome = refresher(engine, store).refreshAfterUnauthorized(freshToken)

        assertEquals(SessionRefreshOutcome.REFRESHED, outcome)
        assertEquals(1, posts, "exp ещё не вышел, но 401 требует ротации")
        assertEquals("new-access", store.snapshot?.accessToken)
        assertEquals("new-refresh", store.snapshot?.refreshToken)
    }

    @Test
    fun `parallel request that already rotated is not refreshed twice`() = runTest {
        val store = StubSessionStore(freshToken)
        // Другой запрос уже сохранил новую пару.
        store.updateTokens(accessToken = "already-new", refreshToken = "new-refresh")
        val engine = MockEngine { error("повторный refresh отозвал бы семью") }

        val outcome = refresher(engine, store).refreshAfterUnauthorized(staleAccessToken = freshToken)

        assertEquals(SessionRefreshOutcome.REFRESHED, outcome)
        assertEquals("already-new", store.snapshot?.accessToken)
    }

    @Test
    fun `rejected refresh reports a dead session`() = runTest {
        val store = StubSessionStore(expiredToken)
        val engine = MockEngine {
            respond(
                """{"error":"refresh_token_reused"}""",
                HttpStatusCode.Unauthorized,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val outcome = refresher(engine, store).refreshAfterUnauthorized(expiredToken)

        assertEquals(SessionRefreshOutcome.REJECTED, outcome)
        assertEquals(expiredToken, store.snapshot?.accessToken, "токены не подменяются")
    }

    @Test
    fun `server error keeps the session for a later retry`() = runTest {
        val store = StubSessionStore(expiredToken)
        val engine = MockEngine {
            respond("upstream unavailable", HttpStatusCode.InternalServerError)
        }

        val outcome = refresher(engine, store).refreshAfterUnauthorized(expiredToken)

        assertEquals(SessionRefreshOutcome.UNAVAILABLE, outcome)
        assertEquals(expiredToken, store.snapshot?.accessToken)
    }

    @Test
    fun `signed out session cannot refresh`() = runTest {
        val store = StubSessionStore(accessToken = null)
        val engine = MockEngine { error("нет сессии — нет запроса") }

        assertFalse(refresher(engine, store).ensureFresh())
    }

    private fun refresher(
        engine: MockEngine,
        store: SessionStore,
    ): KtorSessionRefresher {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }
        return KtorSessionRefresher(
            client = client,
            session = store,
            clock = { 2_000_000 },
        )
    }
}

/** Сессия с заданным access-токеном; запоминает ротацию. */
private class StubSessionStore(accessToken: String?) : SessionStore {

    private val mutableState = MutableStateFlow<SessionState>(
        if (accessToken == null) {
            SessionState.SignedOut
        } else {
            SessionState.SignedIn(
                SessionSnapshot(
                    userId = "user-1",
                    accessToken = accessToken,
                    refreshToken = "old-refresh",
                ),
            )
        },
    )

    override val state: StateFlow<SessionState> = mutableState

    val snapshot: SessionSnapshot?
        get() = (mutableState.value as? SessionState.SignedIn)?.session

    override suspend fun refresh() = Unit

    override suspend fun save(session: SessionSnapshot) {
        mutableState.value = SessionState.SignedIn(session)
    }

    override suspend fun updateTokens(accessToken: String, refreshToken: String) {
        val current = snapshot ?: return
        mutableState.value = SessionState.SignedIn(
            current.copy(accessToken = accessToken, refreshToken = refreshToken),
        )
    }

    override suspend fun clear() {
        mutableState.value = SessionState.SignedOut
    }

    override fun accessToken(): String? = snapshot?.accessToken
}
