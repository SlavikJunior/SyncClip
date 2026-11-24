package io.github.slavikjunior.syncclip.server.app

import io.github.slavikjunior.syncclip.server.contracts.dto.auth.CanaryAttemptRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.CanaryDto
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.GuestExchangeRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.LoginRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.LogoutRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.RefreshRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.RegisterRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.TokenPairDto
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.GuestInviteResponse
import io.github.slavikjunior.syncclip.server.contracts.dto.device.DeviceListDto
import io.github.slavikjunior.syncclip.server.contracts.error.ErrorResponse
import io.github.slavikjunior.syncclip.server.contracts.util.UuidV7
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.delete
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Приёмка §7: регистрация, вход, refresh-ротация, canary/lockout, guest, logout. */
class AuthApiTest {

    private lateinit var database: io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory

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

    @Test
    fun `register returns 201 with token pair and persists device`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val client = jsonClient()
        val request = IntegrationSupport.registerRequest()

        val response = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val tokens: io.github.slavikjunior.syncclip.server.contracts.dto.auth.AuthResponse = response.body()
        assertTrue(tokens.accessToken.isNotBlank())
        assertTrue(tokens.refreshToken.isNotBlank())

        val devices: DeviceListDto = client.get("/v1/devices") {
            header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
        }.body()
        assertEquals(1, devices.devices.size)
        assertEquals(request.deviceId, devices.devices.single().deviceId)
        assertEquals("permanent", devices.devices.single().deviceType)
    }

    @Test
    fun `duplicate email returns 409 with localized message`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val client = jsonClient()
        val request = IntegrationSupport.registerRequest()
        client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val duplicate = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request.copy(deviceId = IntegrationSupport.newDeviceId()))
        }

        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        val error: ErrorResponse = duplicate.body()
        assertEquals("email_already_registered", error.error)

        val localized = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.AcceptLanguage, "ru-RU,ru;q=0.9")
            setBody(request.copy(deviceId = IntegrationSupport.newDeviceId()))
        }.body<ErrorResponse>()
        assertTrue(localized.message.contains("уже"), localized.message)
    }

    @Test
    fun `login rejects wrong password and accepts the right one`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val client = jsonClient()
        val request = IntegrationSupport.registerRequest()
        client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val wrong = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(IntegrationSupport.loginRequest(request.email, "WrongPassword123!", request.deviceId))
        }
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        assertEquals("invalid_credentials", wrong.body<ErrorResponse>().error)

        val ok = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(IntegrationSupport.loginRequest(request.email, password = request.password, deviceId = request.deviceId))
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertTrue(ok.body<io.github.slavikjunior.syncclip.server.contracts.dto.auth.AuthResponse>().accessToken.isNotBlank())
    }

    @Test
    fun `refresh rotates the pair and logout revokes the family`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val client = jsonClient()
        val registered = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(IntegrationSupport.registerRequest())
        }.body<io.github.slavikjunior.syncclip.server.contracts.dto.auth.AuthResponse>()

        val refreshed = client.post("/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(registered.refreshToken))
        }
        assertEquals(HttpStatusCode.OK, refreshed.status)
        val rotated: TokenPairDto = refreshed.body()
        assertTrue(rotated.refreshToken != registered.refreshToken)

        val logout = client.post("/v1/auth/logout") {
            header(HttpHeaders.Authorization, "Bearer ${rotated.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(LogoutRequest(refreshToken = rotated.refreshToken))
        }
        assertEquals(HttpStatusCode.NoContent, logout.status)

        // После logout прежний access больше не проходит: token_version инкрементирован.
        val devicesAfterLogout = client.get("/v1/devices") {
            header(HttpHeaders.Authorization, "Bearer ${rotated.accessToken}")
        }
        assertEquals(HttpStatusCode.Unauthorized, devicesAfterLogout.status)
        assertEquals("token_revoked", devicesAfterLogout.body<ErrorResponse>().error)

        // Отозванный refresh нельзя использовать повторно.
        val reuse = client.post("/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(rotated.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, reuse.status)
    }

    @Test
    fun `canary is set once and lockout follows 3 failures`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val client = jsonClient()
        val tokens = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(IntegrationSupport.registerRequest())
        }.body<io.github.slavikjunior.syncclip.server.contracts.dto.auth.AuthResponse>()

        val missing = client.get("/v1/auth/canary") {
            header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
        }
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertEquals("canary_not_found", missing.body<ErrorResponse>().error)

        val canary = Base64.getEncoder().encodeToString(ByteArray(80) { it.toByte() })
        val created = client.post("/v1/auth/canary") {
            header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(CanaryDto(canary))
        }
        assertEquals(HttpStatusCode.Created, created.status)

        val again = client.post("/v1/auth/canary") {
            header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(CanaryDto(canary))
        }
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals("canary_already_set", again.body<ErrorResponse>().error)

        val fetched: CanaryDto = client.get("/v1/auth/canary") {
            header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
        }.body()
        assertEquals(canary, fetched.encryptedCanary)

        val first = attempt(client, tokens.accessToken, success = false)
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(2, first.body<io.github.slavikjunior.syncclip.server.contracts.dto.auth.CanaryAttemptResponse>().remainingAttempts)

        val second = attempt(client, tokens.accessToken, success = false)
        assertEquals(1, second.body<io.github.slavikjunior.syncclip.server.contracts.dto.auth.CanaryAttemptResponse>().remainingAttempts)

        val third = attempt(client, tokens.accessToken, success = false)
        assertEquals(HttpStatusCode.Forbidden, third.status)
        val locked: ErrorResponse = third.body()
        assertEquals("master_password_locked", locked.error)
        assertEquals(300L, locked.retryAfter)

        // Попытка при активной блокировке не инкрементирует счётчик (остаётся 403).
        val duringLock = attempt(client, tokens.accessToken, success = false)
        assertEquals(HttpStatusCode.Forbidden, duringLock.status)

        // Успех сбрасывает счётчик и блокировку.
        val successAttempt = attempt(client, tokens.accessToken, success = true)
        assertEquals(HttpStatusCode.OK, successAttempt.status)
        assertEquals(3, successAttempt.body<io.github.slavikjunior.syncclip.server.contracts.dto.auth.CanaryAttemptResponse>().remainingAttempts)
    }

    @Test
    fun `guest invite exchange gives access only and forbids management`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val client = jsonClient()
        val owner = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(IntegrationSupport.registerRequest())
        }.body<io.github.slavikjunior.syncclip.server.contracts.dto.auth.AuthResponse>()

        val invite: GuestInviteResponse = client.post("/v1/auth/guest/invite") {
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
        }.body()
        assertTrue(invite.inviteToken.isNotBlank())
        assertTrue(invite.shareUri.startsWith("syncclip://guest?token="))

        val guestDeviceId = IntegrationSupport.newDeviceId()
        val exchanged = client.post("/v1/auth/guest/exchange") {
            contentType(ContentType.Application.Json)
            setBody(GuestExchangeRequest(invite.inviteToken, guestDeviceId, "Guest iPhone", "iOS"))
        }
        assertEquals(HttpStatusCode.Created, exchanged.status)
        val guest: io.github.slavikjunior.syncclip.server.contracts.dto.auth.GuestExchangeResponse = exchanged.body()
        assertEquals(guestDeviceId, guest.deviceId)

        // У гостя нет refresh — только access.
        val forbidden = client.get("/v1/devices") {
            header(HttpHeaders.Authorization, "Bearer ${guest.accessToken}")
        }
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        assertEquals("forbidden_for_guest", forbidden.body<ErrorResponse>().error)

        // Устройство гостя видно владельцу, но не учитывается в лимите.
        val devices: DeviceListDto = client.get("/v1/devices") {
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
        }.body()
        assertEquals(2, devices.devices.size)
        assertTrue(devices.devices.any { it.deviceType == "guest" })

        // Повторный обмен погашенного приглашения — 400 с контрактным кодом (FUNC-10).
        val reused = client.post("/v1/auth/guest/exchange") {
            contentType(ContentType.Application.Json)
            setBody(GuestExchangeRequest(invite.inviteToken, IntegrationSupport.newDeviceId(), "Other", "Android"))
        }
        assertEquals(HttpStatusCode.BadRequest, reused.status)
        assertEquals("guest_invite_already_used", reused.body<ErrorResponse>().error)
    }

    @Test
    fun `fourth permanent device is rejected with device limit`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val client = jsonClient()
        val register = IntegrationSupport.registerRequest()
        client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(register)
        }

        repeat(2) {
            val response = client.post("/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(IntegrationSupport.loginRequest(register.email, register.password, IntegrationSupport.newDeviceId()))
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

        val fourth = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(IntegrationSupport.loginRequest(register.email, register.password, IntegrationSupport.newDeviceId()))
        }
        assertEquals(HttpStatusCode.Forbidden, fourth.status)
        assertEquals("device_limit_exceeded", fourth.body<ErrorResponse>().error)
    }

    @Test
    fun `device rename broadcasts and delete removes device`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val client = jsonClient()
        val register = IntegrationSupport.registerRequest()
        val tokens = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(register)
        }.body<io.github.slavikjunior.syncclip.server.contracts.dto.auth.AuthResponse>()

        val second = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(IntegrationSupport.loginRequest(register.email, register.password, IntegrationSupport.newDeviceId()))
        }.body<io.github.slavikjunior.syncclip.server.contracts.dto.auth.AuthResponse>()

        val renamed = client.patch("/v1/devices/${register.deviceId}") {
            header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(mapOf("name" to "Work Laptop", "icon" to "LAPTOP"))
        }
        assertEquals(HttpStatusCode.OK, renamed.status)
        assertEquals(
            "Work Laptop",
            renamed.body<io.github.slavikjunior.syncclip.server.contracts.dto.device.DeviceEnvelope>().device.name,
        )

        val invalid = client.patch("/v1/devices/${register.deviceId}") {
            header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(mapOf("name" to "macOS - 192.168.1.10"))
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)

        val deleted = client.delete("/v1/devices/${second.userId.let { _ -> findSecondDeviceId(client, tokens.accessToken, register.deviceId) }}") {
            header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
        }
        assertEquals(HttpStatusCode.NoContent, deleted.status)

        val devices: DeviceListDto = client.get("/v1/devices") {
            header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
        }.body()
        assertEquals(1, devices.devices.size)
    }

    @Test
    fun `unread notifications expose failed master password attempts`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val client = jsonClient()
        val tokens = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(IntegrationSupport.registerRequest())
        }.body<io.github.slavikjunior.syncclip.server.contracts.dto.auth.AuthResponse>()

        attempt(client, tokens.accessToken, success = false)

        val unread = client.get("/v1/notifications/unread") {
            header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, unread.status)
        val body = unread.body<io.github.slavikjunior.syncclip.server.contracts.dto.notification.UnreadNotificationsResponse>()
        assertTrue(body.items.any { it.type == "master_password" })
    }

    @Test
    fun `fcm token registration accepts own device and rejects foreign`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val client = jsonClient()
        val request = IntegrationSupport.registerRequest()
        val tokens = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<io.github.slavikjunior.syncclip.server.contracts.dto.auth.AuthResponse>()

        val ok = client.post("/v1/fcm/register") {
            header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(io.github.slavikjunior.syncclip.server.contracts.dto.fcm.FcmRegisterRequest(request.deviceId, "fcm-token-1"))
        }
        assertEquals(HttpStatusCode.OK, ok.status)

        val foreign = client.post("/v1/fcm/register") {
            header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(
                io.github.slavikjunior.syncclip.server.contracts.dto.fcm.FcmRegisterRequest(
                    IntegrationSupport.newDeviceId(),
                    "fcm-token-2",
                ),
            )
        }
        assertEquals(HttpStatusCode.Forbidden, foreign.status)
        assertEquals("device_mismatch", foreign.body<ErrorResponse>().error)

        val invalidToken = client.post("/v1/fcm/register") {
            header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
            contentType(ContentType.Application.Json)
            setBody(io.github.slavikjunior.syncclip.server.contracts.dto.fcm.FcmRegisterRequest(request.deviceId, "  "))
        }
        assertEquals(HttpStatusCode.BadRequest, invalidToken.status)
    }

    private suspend fun attempt(client: HttpClient, token: String, success: Boolean): HttpResponse =
        client.post("/v1/auth/canary/attempt") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CanaryAttemptRequest(success))
        }

    private suspend fun findSecondDeviceId(client: HttpClient, token: String, ownDeviceId: String): String {
        val devices: DeviceListDto = client.get("/v1/devices") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body()
        return assertNotNull(devices.devices.firstOrNull { it.deviceId != ownDeviceId }).deviceId
    }

    private fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
        install(ContentNegotiation) { json(io.github.slavikjunior.syncclip.server.app.plugins.ServerJson) }
    }
}
