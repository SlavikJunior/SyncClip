package io.github.slavikjunior.syncclip.server.app

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.sun.net.httpserver.HttpServer
import io.github.slavikjunior.syncclip.server.app.plugins.ServerJson
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.AuthResponse
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.FirebaseLoginRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.RegisterRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.device.DeviceListDto
import io.github.slavikjunior.syncclip.server.contracts.error.ErrorResponse
import io.github.slavikjunior.syncclip.server.core.config.AppConfig
import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * SEC-10: additive Firebase Auth.
 *
 * Полный путь без Firebase-проекта: локальный HTTP JWKS с self-signed RSA
 * сертификатом и ID token, подписанный соответствующим приватным ключом.
 * В firebase-режиме password register/login не регистрируются (404).
 */
class FirebaseAuthTest {
    private lateinit var database: DatabaseFactory
    private lateinit var jwksServer: HttpServer
    private lateinit var jwksUrl: String
    private lateinit var privateKey: RSAPrivateKey

    @BeforeEach
    fun setUp() {
        IntegrationSupport.assumeDatabase()
        database = IntegrationSupport.database()
        IntegrationSupport.truncate(database)
        privateKey = loadPrivateKey()
        jwksServer = startJwksServer()
        jwksUrl = "http://127.0.0.1:${jwksServer.address.port}/jwks"
    }

    @AfterEach
    fun tearDown() {
        if (::jwksServer.isInitialized) jwksServer.stop(0)
        if (::database.isInitialized) database.close()
    }

    @Test
    fun `firebase identity creates account and session while password routes are disabled`() =
        testApplication {
            application { module(firebaseConfig(), database) }
            val client = jsonClient()

            // password-маршруты в firebase-режиме отсутствуют → enumeration невозможен.
            assertEquals(HttpStatusCode.NotFound, client.post("/v1/auth/register") { setBody("{}") }.status)
            assertEquals(HttpStatusCode.NotFound, client.post("/v1/auth/login") { setBody("{}") }.status)

            val token = firebaseToken(uid = "firebase-uid-1", email = "fb-1@example.com", verified = true)
            val deviceId = IntegrationSupport.newDeviceId()
            val response =
                client.post("/v1/auth/firebase") {
                    contentType(ContentType.Application.Json)
                    setBody(FirebaseLoginRequest(token, deviceId, "Pixel 8", "Android"))
                }
            assertEquals(HttpStatusCode.OK, response.status)
            val session = response.body<AuthResponse>()
            assertTrue(session.accessToken.isNotBlank())

            val devices =
                client.get("/v1/devices") {
                    header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
                }
            assertEquals(HttpStatusCode.OK, devices.status)
            assertEquals(
                deviceId,
                devices
                    .body<DeviceListDto>()
                    .devices
                    .single()
                    .deviceId,
            )

            // Повторный вход тем же Firebase identity на другом устройстве — тот же аккаунт.
            val secondDevice = IntegrationSupport.newDeviceId()
            val second =
                client
                    .post("/v1/auth/firebase") {
                        contentType(ContentType.Application.Json)
                        setBody(FirebaseLoginRequest(token, secondDevice, "Pixel 9", "Android"))
                    }.body<AuthResponse>()
            assertEquals(session.userId, second.userId)
        }

    @Test
    fun `invalid and unverified firebase tokens are rejected`() =
        testApplication {
            application { module(firebaseConfig(), database) }
            val client = jsonClient()
            val deviceId = IntegrationSupport.newDeviceId()

            val invalid =
                client.post("/v1/auth/firebase") {
                    contentType(ContentType.Application.Json)
                    setBody(FirebaseLoginRequest("not-a-jwt", deviceId, "Pixel", "Android"))
                }
            assertEquals(HttpStatusCode.Unauthorized, invalid.status)
            assertEquals("firebase_token_invalid", invalid.body<ErrorResponse>().error)

            val unverified = firebaseToken(uid = "firebase-uid-2", email = "fb-2@example.com", verified = false)
            val rejected =
                client.post("/v1/auth/firebase") {
                    contentType(ContentType.Application.Json)
                    setBody(FirebaseLoginRequest(unverified, deviceId, "Pixel", "Android"))
                }
            assertEquals(HttpStatusCode.Forbidden, rejected.status)
            assertEquals("email_not_verified", rejected.body<ErrorResponse>().error)
        }

    @Test
    fun `both mode lets desktop password account link to firebase login`() =
        testApplication {
            application { module(bothConfig(), database) }
            val client = jsonClient()
            val email = "mixed-1@example.com"
            val password = "MixedPassword123!"

            // Desktop (JVM без Firebase) регистрируется серверным паролем.
            val desktop =
                client.post("/v1/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody(RegisterRequest(email, password, IntegrationSupport.newDeviceId(), "MacBook", "macOS"))
                }
            assertEquals(HttpStatusCode.Created, desktop.status)
            val desktopSession = desktop.body<AuthResponse>()

            // Android входит через Firebase с тем же email — сервер привязывает UID
            // к существующему аккаунту, а не создаёт второй.
            val token = firebaseToken(uid = "firebase-uid-mixed", email = email, verified = true)
            val android =
                client.post("/v1/auth/firebase") {
                    contentType(ContentType.Application.Json)
                    setBody(FirebaseLoginRequest(token, IntegrationSupport.newDeviceId(), "Pixel 8", "Android"))
                }
            assertEquals(HttpStatusCode.OK, android.status)
            assertEquals(desktopSession.userId, android.body<AuthResponse>().userId)
        }

    @Test
    fun `password mode keeps firebase endpoint absent`() =
        testApplication {
            application { module(IntegrationSupport.config(), database) }
            val client = jsonClient()
            val response =
                client.post("/v1/auth/firebase") {
                    contentType(ContentType.Application.Json)
                    setBody(FirebaseLoginRequest("token", IntegrationSupport.newDeviceId(), "Pixel", "Android"))
                }
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    // ------------------------------------------------------------------ helpers

    private fun firebaseConfig(): AppConfig =
        IntegrationSupport.config(
            mapOf(
                "AUTH_PROVIDER" to "firebase",
                "FIREBASE_PROJECT_ID" to "test-project",
                "FIREBASE_JWKS_URL" to jwksUrl,
                "FIREBASE_REQUIRE_VERIFIED_EMAIL" to "true",
            ),
        )

    private fun bothConfig(): AppConfig =
        IntegrationSupport.config(
            mapOf(
                "AUTH_PROVIDER" to "both",
                "FIREBASE_PROJECT_ID" to "test-project",
                "FIREBASE_JWKS_URL" to jwksUrl,
                "FIREBASE_REQUIRE_VERIFIED_EMAIL" to "true",
            ),
        )

    private fun firebaseToken(
        uid: String,
        email: String,
        verified: Boolean,
    ): String {
        val now = Instant.now()
        return JWT
            .create()
            .withIssuer("https://securetoken.google.com/test-project")
            .withAudience("test-project")
            .withSubject(uid)
            .withKeyId("test-key")
            .withClaim("email", email)
            .withClaim("email_verified", verified)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plusSeconds(3_600)))
            .sign(Algorithm.RSA256(privateKey))
    }

    private fun startJwksServer(): HttpServer {
        val certPem = javaClass.getResource("/firebase/cert.pem")!!.readText()
        val body = buildJsonObject { put("test-key", certPem) }.toString().toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/jwks") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("Cache-Control", "max-age=300")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        return server
    }

    private fun loadPrivateKey(): RSAPrivateKey {
        val pem =
            javaClass
                .getResource("/firebase/key.pem")!!
                .readText()
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")
        val bytes = Base64.getDecoder().decode(pem)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes)) as RSAPrivateKey
    }

    private fun ApplicationTestBuilder.jsonClient(): HttpClient =
        createClient {
            install(ContentNegotiation) { json(ServerJson) }
        }
}
