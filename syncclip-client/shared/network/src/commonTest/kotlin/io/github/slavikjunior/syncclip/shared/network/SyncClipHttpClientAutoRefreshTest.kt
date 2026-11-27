package io.github.slavikjunior.syncclip.shared.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Автоматический refresh REST по `401`: один повтор с новой парой, при
 * отвергнутом refresh — завершение сессии, при сетевой ошибке — без разлогина.
 */
internal class SyncClipHttpClientAutoRefreshTest {

    @Test
    fun `401 refreshes the pair and retries once with the new token`() = runTest {
        var token = "old-access"
        var refreshes = 0
        val seen = mutableListOf<String?>()
        // Первый ответ 401, повтор — успешный: отличаем по счётчику запросов.
        val mock = MockEngine { request ->
            seen += request.headers[HttpHeaders.Authorization]
            if (seen.size == 1) {
                respond("", HttpStatusCode.Unauthorized)
            } else {
                respond("", HttpStatusCode.OK)
            }
        }
        val client = SyncClipHttpClient(
            baseUrl = "http://test",
            tokenProvider = { token },
            tokenRefresher = TokenRefresher { stale ->
                assertEquals("old-access", stale, "наружу уходит токен из 401-запроса")
                refreshes++
                token = "new-access"
                TokenRefreshOutcome.REFRESHED
            },
            engine = mock,
        )

        val response = client.client.get("/v1/devices")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, seen.size, "ровно один повтор")
        assertEquals("Bearer old-access", seen[0])
        assertEquals("Bearer new-access", seen[1], "повтор идёт с обновлённым токеном")
        assertEquals(1, refreshes)
    }

    @Test
    fun `second 401 is returned without a third attempt`() = runTest {
        var refreshes = 0
        val engine = MockEngine {
            respond("", HttpStatusCode.Unauthorized)
        }
        val client = SyncClipHttpClient(
            baseUrl = "http://test",
            tokenProvider = { "token" },
            tokenRefresher = TokenRefresher {
                refreshes++
                TokenRefreshOutcome.REFRESHED
            },
            engine = engine,
        )

        val response = client.client.get("/v1/devices")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(2, engine.requestHistory.size, "после повтора не зацикливаемся")
        assertEquals(1, refreshes)
    }

    @Test
    fun `rejected refresh ends the session and does not retry`() = runTest {
        var expired = false
        val engine = MockEngine {
            respond("", HttpStatusCode.Unauthorized)
        }
        val client = SyncClipHttpClient(
            baseUrl = "http://test",
            tokenProvider = { "dead-token" },
            tokenRefresher = TokenRefresher { TokenRefreshOutcome.REJECTED },
            onSessionExpired = { expired = true },
            engine = engine,
        )

        val response = client.client.get("/v1/devices")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(expired, "сессия мертва")
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun `unavailable refresh keeps the session`() = runTest {
        var expired = false
        val engine = MockEngine {
            respond("", HttpStatusCode.Unauthorized)
        }
        val client = SyncClipHttpClient(
            baseUrl = "http://test",
            tokenProvider = { "token" },
            tokenRefresher = TokenRefresher { TokenRefreshOutcome.UNAVAILABLE },
            onSessionExpired = { expired = true },
            engine = engine,
        )

        val response = client.client.get("/v1/devices")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertFalse(expired, "таймаут сети не должен разлогинивать")
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun `refresh endpoint itself is not intercepted`() = runTest {
        var refreshes = 0
        val engine = MockEngine {
            respond("""{"error":"token_expired"}""", HttpStatusCode.Unauthorized)
        }
        val client = SyncClipHttpClient(
            baseUrl = "http://test",
            tokenProvider = { "token" },
            tokenRefresher = TokenRefresher {
                refreshes++
                TokenRefreshOutcome.REFRESHED
            },
            engine = engine,
        )

        val response = client.client.post("/v1/auth/refresh")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(1, engine.requestHistory.size)
        assertEquals(0, refreshes, "refresh не должен рекурсивно обновлять сам себя")
    }

    @Test
    fun `websocket handshake is not retried by the interceptor`() = runTest {
        var refreshes = 0
        val engine = MockEngine {
            respond("", HttpStatusCode.Unauthorized)
        }
        val client = SyncClipHttpClient(
            baseUrl = "http://test",
            tokenProvider = { "token" },
            tokenRefresher = TokenRefresher {
                refreshes++
                TokenRefreshOutcome.REFRESHED
            },
            engine = engine,
        )

        val response = client.client.get("/v1/sync") {
            attributes.put(NO_AUTO_REFRESH, true)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(1, engine.requestHistory.size, "переподключением управляет SyncTransport")
        assertEquals(0, refreshes)
    }

    @Test
    fun `successful response is not disturbed`() = runTest {
        var refreshes = 0
        val engine = MockEngine {
            respond("ok", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/plain"))
        }
        val client = SyncClipHttpClient(
            baseUrl = "http://test",
            tokenProvider = { null },
            tokenRefresher = TokenRefresher {
                refreshes++
                TokenRefreshOutcome.REFRESHED
            },
            engine = engine,
        )

        val response = client.client.get("/public/ping")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, engine.requestHistory.size)
        assertEquals(0, refreshes)
        assertNull(engine.requestHistory.single().headers[HttpHeaders.Authorization])
    }
}
