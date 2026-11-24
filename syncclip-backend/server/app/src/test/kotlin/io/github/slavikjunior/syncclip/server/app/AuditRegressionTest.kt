package io.github.slavikjunior.syncclip.server.app

import io.github.slavikjunior.syncclip.server.app.plugins.ServerJson
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.AuthResponse
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.GuestExchangeRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.GuestExchangeResponse
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.GuestInviteResponse
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.LogoutRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.RefreshRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.TokenPairDto
import io.github.slavikjunior.syncclip.server.contracts.dto.device.DeviceListDto
import io.github.slavikjunior.syncclip.server.contracts.dto.fcm.FcmRegisterRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.notification.UnreadNotificationsResponse
import io.github.slavikjunior.syncclip.server.contracts.error.ErrorResponse
import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Регрессионные тесты по аудиту 2026-11-24: гостевые привилегии, refresh-гонка,
 * маппинг 4xx, пагинация, login-lockout, нормализация deviceId/osType.
 */
class AuditRegressionTest {

    private lateinit var database: DatabaseFactory

    @BeforeEach
    fun setUp() {
        IntegrationSupport.assumeDatabase()
        database = IntegrationSupport.database()
        IntegrationSupport.truncate(database)
    }

    @AfterEach
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    // ------------------------------------------------------------ SEC-01/02/03

    @Test
    fun `guest cannot revoke all account sessions`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val client = jsonClient()
        val owner = registerOwner(client)
        val guest = createGuest(client, owner.accessToken)

        val forbidden = client.post("/v1/auth/logout") {
            header(HttpHeaders.Authorization, "Bearer ${guest.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(LogoutRequest(allDevices = true))
        }
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        assertEquals("forbidden_for_guest", forbidden.body<ErrorResponse>().error)

        // Сессия владельца не тронута.
        assertEquals(HttpStatusCode.OK, client.get("/v1/devices") { bearer(owner.accessToken) }.status)
    }

    @Test
    fun `guest cannot read security feed or register fcm`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val client = jsonClient()
        val owner = registerOwner(client)
        val guest = createGuest(client, owner.accessToken)

        // Владелец накопил security-событие.
        client.post("/v1/auth/canary/attempt") {
            bearer(owner.accessToken)
            contentType(ContentType.Application.Json)
            setBody(mapOf("success" to false))
        }

        val feed = client.get("/v1/notifications/unread") { bearer(guest.accessToken) }
        assertEquals(HttpStatusCode.Forbidden, feed.status)
        assertEquals("forbidden_for_guest", feed.body<ErrorResponse>().error)

        val fcm = client.post("/v1/fcm/register") {
            bearer(guest.accessToken)
            contentType(ContentType.Application.Json)
            setBody(FcmRegisterRequest(guest.deviceId, "guest-fcm-token"))
        }
        assertEquals(HttpStatusCode.Forbidden, fcm.status)
        assertEquals("forbidden_for_guest", fcm.body<ErrorResponse>().error)
    }

    // ---------------------------------------------------------------- SEC-04

    @Test
    fun `concurrent refresh keeps a single active refresh token`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val client = jsonClient()
        val owner = registerOwner(client)
        var refresh = owner.refreshToken

        repeat(5) {
            // Новый логин на том же устройстве выдаёт свежее семейство.
            val login = client.post("/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(
                    IntegrationSupport.loginRequest(
                        email = owner.email,
                        password = owner.password,
                        deviceId = owner.deviceId,
                    ),
                )
            }.body<AuthResponse>()
            refresh = login.refreshToken

            val responses = coroutineScope {
                List(2) {
                    async(Dispatchers.IO) {
                        client.post("/v1/auth/refresh") {
                            contentType(ContentType.Application.Json)
                            setBody(RefreshRequest(refresh))
                        }
                    }
                }.awaitAll()
            }
            assertTrue(responses.any { it.status == HttpStatusCode.OK }, "ни один refresh не прошёл")
            assertEquals(
                1,
                activeRefreshTokens(owner.deviceId),
                "после гонки у устройства должен остаться ровно один активный refresh-токен",
            )
            refresh = responses.first { it.status == HttpStatusCode.OK }.body<TokenPairDto>().refreshToken
        }
    }

    // ---------------------------------------------------------------- FUNC-07

    @Test
    fun `superseded refresh does not kill the new session`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val client = jsonClient()
        val owner = registerOwner(client)

        val relogin = login(client, owner, owner.password).body<AuthResponse>()

        // Старый (superseded) refresh недействителен, но не должен отзывать новую сессию.
        val oldRefresh = client.post("/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(owner.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, oldRefresh.status)

        val devices = client.get("/v1/devices") { bearer(relogin.accessToken) }
        assertEquals(HttpStatusCode.OK, devices.status)

        val rotated = client.post("/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(relogin.refreshToken))
        }
        assertEquals(HttpStatusCode.OK, rotated.status)
    }

    // ---------------------------------------------------------------- SEC-09

    @Test
    fun `same device id can belong to different accounts`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val client = jsonClient()
        val sharedDeviceId = IntegrationSupport.newDeviceId()

        val first = IntegrationSupport.registerRequest(deviceId = sharedDeviceId)
        val firstTokens = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(first)
        }.body<AuthResponse>()

        val second = IntegrationSupport.registerRequest(deviceId = sharedDeviceId)
        val secondResponse = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(second)
        }
        assertEquals(HttpStatusCode.Created, secondResponse.status, "deviceId должен быть свободен в другом аккаунте")
        val secondTokens = secondResponse.body<AuthResponse>()

        // Каждый видит только своё устройство.
        val firstDevices = client.get("/v1/devices") { bearer(firstTokens.accessToken) }.body<DeviceListDto>()
        val secondDevices = client.get("/v1/devices") { bearer(secondTokens.accessToken) }.body<DeviceListDto>()
        assertEquals(listOf(sharedDeviceId), firstDevices.devices.map { it.deviceId })
        assertEquals(listOf(sharedDeviceId), secondDevices.devices.map { it.deviceId })

        // Logout первого аккаунта с чужим refresh-токеном не должен отзывать второй аккаунт.
        val logout = client.post("/v1/auth/logout") {
            bearer(firstTokens.accessToken)
            contentType(ContentType.Application.Json)
            setBody(LogoutRequest(refreshToken = secondTokens.refreshToken))
        }
        assertEquals(HttpStatusCode.NoContent, logout.status)

        val secondRefresh = client.post("/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(secondTokens.refreshToken))
        }
        assertEquals(HttpStatusCode.OK, secondRefresh.status)

        // Сессии первого аккаунта отозваны его собственным logout.
        val firstAfterLogout = client.get("/v1/devices") { bearer(firstTokens.accessToken) }
        assertEquals(HttpStatusCode.Unauthorized, firstAfterLogout.status)
    }

    // ---------------------------------------------------------------- SEC-06

    @Test
    fun `login lockout blocks correct password but is masked as 401`() = testApplication {
        application {
            module(
                IntegrationSupport.config(
                    mapOf(
                        "LOGIN_LOCKOUT_ENABLED" to "true",
                        "LOGIN_LOCKOUT_FAILURES" to "2",
                        "LOGIN_LOCKOUT_WINDOW_MINUTES" to "15",
                        "LOGIN_LOCKOUT_MINUTES" to "15",
                    ),
                ),
                database,
            )
        }
        val client = jsonClient()
        val owner = registerOwner(client)

        val wrong1 = login(client, owner, "WrongPassword1!")
        assertEquals(HttpStatusCode.Unauthorized, wrong1.status)
        val wrong2 = login(client, owner, "WrongPassword2!")
        assertEquals(HttpStatusCode.Unauthorized, wrong2.status)
        assertEquals("invalid_credentials", wrong2.body<ErrorResponse>().error)

        // Правильный пароль не обходит блокировку, но ответ не раскрывает lockout (SEC-10).
        val correct = login(client, owner, owner.password)
        assertEquals(HttpStatusCode.Unauthorized, correct.status)
        assertEquals("invalid_credentials", correct.body<ErrorResponse>().error)
    }

    // ---------------------------------------------------------------- FUNC-01

    @Test
    fun `client-side request errors are 4xx with error body`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val client = jsonClient()

        val malformed = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("{not-json")
        }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        assertEquals("validation_error", malformed.body<ErrorResponse>().error)

        val empty = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("")
        }
        assertEquals(HttpStatusCode.BadRequest, empty.status)
        assertEquals("validation_error", empty.body<ErrorResponse>().error)

        val unsupported = client.post("/v1/auth/login") {
            contentType(ContentType.Text.Plain)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.UnsupportedMediaType, unsupported.status)
        assertEquals("unsupported_media_type", unsupported.body<ErrorResponse>().error)

        val oversized = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("x".repeat(1_100_000))
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, oversized.status)
        assertEquals("payload_too_large", oversized.body<ErrorResponse>().error)

        val wrongMethod = client.get("/v1/auth/login")
        assertEquals(HttpStatusCode.MethodNotAllowed, wrongMethod.status)
        assertEquals("method_not_allowed", wrongMethod.body<ErrorResponse>().error)
    }

    // ---------------------------------------------------------------- FUNC-03

    @Test
    fun `unread feed pagination does not repeat the last item`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val client = jsonClient()
        val owner = registerOwner(client)

        repeat(2) {
            client.post("/v1/auth/canary/attempt") {
                bearer(owner.accessToken)
                contentType(ContentType.Application.Json)
                setBody(mapOf("success" to false))
            }
            Thread.sleep(5)
        }

        val page1 = client.get("/v1/notifications/unread") {
            bearer(owner.accessToken)
            parameter("limit", 1)
        }.body<UnreadNotificationsResponse>()
        val cursor = page1.nextCursor
        assertTrue(cursor != null)

        val page2 = client.get("/v1/notifications/unread") {
            bearer(owner.accessToken)
            parameter("limit", 1)
            parameter("since", cursor)
        }.body<UnreadNotificationsResponse>()

        assertTrue(page1.items.isNotEmpty())
        assertTrue(page2.items.none { it.id == page1.items.first().id }, "page2 повторила page1")
    }

    // ---------------------------------------------------------------- FUNC-08

    @Test
    fun `login updates os type of an existing device`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val client = jsonClient()
        val owner = registerOwner(client)

        client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(
                IntegrationSupport.loginRequest(
                    email = owner.email,
                    password = owner.password,
                    deviceId = owner.deviceId,
                    osType = "iOS",
                ),
            )
        }

        val devices = client.get("/v1/devices") { bearer(owner.accessToken) }.body<DeviceListDto>()
        assertEquals("iOS", devices.devices.single { it.deviceId == owner.deviceId }.osType)
    }

    // ---------------------------------------------------------------- LOW-08

    @Test
    fun `feed reports device model from header and user agent separately`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val client = jsonClient()
        val request = IntegrationSupport.registerRequest()
        val tokens = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            header("X-Device-Model", "Pixel 9")
            header(HttpHeaders.UserAgent, "AuditAgent/1.0")
            setBody(request)
        }.body<AuthResponse>()

        client.post("/v1/auth/canary/attempt") {
            bearer(tokens.accessToken)
            contentType(ContentType.Application.Json)
            header("X-Device-Model", "Pixel 9")
            header(HttpHeaders.UserAgent, "AuditAgent/1.0")
            setBody(mapOf("success" to false))
        }

        val feed = client.get("/v1/notifications/unread") { bearer(tokens.accessToken) }
            .body<UnreadNotificationsResponse>()
        val item = feed.items.first { it.type == "master_password" }
        assertEquals("Pixel 9", item.deviceModel)
        assertEquals("AuditAgent/1.0", item.userAgent)
    }

    // ---------------------------------------------------------------- LOW-09

    @Test
    fun `fcm token longer than btree-friendly limit is rejected`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val client = jsonClient()
        val owner = registerOwner(client)

        val tooLong = client.post("/v1/fcm/register") {
            bearer(owner.accessToken)
            contentType(ContentType.Application.Json)
            setBody(FcmRegisterRequest(owner.deviceId, "t".repeat(2_100)))
        }
        assertEquals(HttpStatusCode.BadRequest, tooLong.status)
        assertEquals("validation_error", tooLong.body<ErrorResponse>().error)
    }

    // ------------------------------------------------------------------ helpers

    private data class Owner(
        val accessToken: String,
        val refreshToken: String,
        val deviceId: String,
        val email: String,
        val password: String,
    )

    private data class Guest(val deviceId: String, val accessToken: String)

    private suspend fun registerOwner(client: HttpClient): Owner {
        val request = IntegrationSupport.registerRequest()
        val tokens = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<AuthResponse>()
        return Owner(tokens.accessToken, tokens.refreshToken, request.deviceId, request.email, request.password)
    }

    private suspend fun createGuest(client: HttpClient, ownerAccessToken: String): Guest {
        val invite = client.post("/v1/auth/guest/invite") {
            bearer(ownerAccessToken)
        }.body<GuestInviteResponse>()
        val deviceId = IntegrationSupport.newDeviceId()
        val guest = client.post("/v1/auth/guest/exchange") {
            contentType(ContentType.Application.Json)
            setBody(GuestExchangeRequest(invite.inviteToken, deviceId, "Guest", "iOS"))
        }.body<GuestExchangeResponse>()
        return Guest(guest.deviceId, guest.accessToken)
    }

    private suspend fun login(client: HttpClient, owner: Owner, password: String): HttpResponse =
        client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(
                IntegrationSupport.loginRequest(
                    email = owner.email,
                    password = password,
                    deviceId = owner.deviceId,
                ),
            )
        }

    private fun activeRefreshTokens(deviceId: String): Int =
        database.dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT count(*) FROM refresh_tokens WHERE device_id = CAST(? AS uuid) AND revoked_at IS NULL",
            ).use { statement ->
                statement.setString(1, deviceId)
                statement.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
        install(ContentNegotiation) { json(ServerJson) }
    }
}
