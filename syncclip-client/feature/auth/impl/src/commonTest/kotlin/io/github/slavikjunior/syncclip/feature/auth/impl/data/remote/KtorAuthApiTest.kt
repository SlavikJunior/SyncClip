package io.github.slavikjunior.syncclip.feature.auth.impl.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Контракт клиента с сервером: путь `/v1/...` и JSON-тело запроса.
 *
 * Регрессия, которую ловит тест: без `contentType(Json)` Ktor падает с
 * «Fail to prepare request body», а путь без `/v1` даёт 404.
 */
class KtorAuthApiTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `login posts firebase token to v1 endpoint as json`() = runTest {
        var path: String? = null
        var body: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            body = request.body.toByteArray().decodeToString()
            respond(
                content = """{"userId":"u1","accessToken":"a1","refreshToken":"r1"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val api = KtorAuthApi(HttpClient(engine) { install(ContentNegotiation) { json(json) } })

        val tokens = api.login(
            FirebaseLoginRequestDto(
                idToken = "firebase-token",
                deviceId = "device-1",
                deviceName = "Test",
                osType = "Android",
            ),
        ).getOrThrow()

        assertEquals("a1", tokens.accessToken)
        assertEquals("/v1/auth/firebase", path)
        assertTrue(body.orEmpty().contains("\"idToken\":\"firebase-token\""))
    }

    @Test
    fun `server error status maps to http exception`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"invalid_credentials","message":"bad"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val api = KtorAuthApi(HttpClient(engine) { install(ContentNegotiation) { json(json) } })

        val error = api.login(FirebaseLoginRequestDto("t", "d", "n", "Android")).exceptionOrNull()

        assertTrue(error is AuthHttpException)
        assertEquals(401, (error as AuthHttpException).statusCode)
        assertEquals("invalid_credentials", error.errorCode)
    }

    @Test
    fun `password login posts to v1 auth login`() = runTest {
        var path: String? = null
        var body: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            body = request.body.toByteArray().decodeToString()
            respond(
                content = """{"userId":"u1","accessToken":"a1","refreshToken":"r1"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val api = KtorAuthApi(HttpClient(engine) { install(ContentNegotiation) { json(json) } })

        val tokens = api.loginPassword(
            PasswordAuthRequestDto(
                email = "user@example.com",
                password = "secret123",
                deviceId = "device-1",
                deviceName = "Desktop",
                osType = "Linux",
            ),
        ).getOrThrow()

        assertEquals("/v1/auth/login", path)
        assertEquals("u1", tokens.userId)
        assertTrue(body.orEmpty().contains("\"email\":\"user@example.com\""))
        assertTrue(body.orEmpty().contains("\"osType\":\"Linux\""))
    }

    @Test
    fun `password register posts to v1 auth register`() = runTest {
        var path: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            respond(
                content = """{"userId":"u2","accessToken":"a2","refreshToken":"r2"}""",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val api = KtorAuthApi(HttpClient(engine) { install(ContentNegotiation) { json(json) } })

        val tokens = api.registerPassword(
            PasswordAuthRequestDto("new@example.com", "secret123", "d", "Desktop", "Linux"),
        ).getOrThrow()

        assertEquals("/v1/auth/register", path)
        assertEquals("u2", tokens.userId)
    }
}
