package io.github.slavikjunior.syncclip.server.app

import io.github.slavikjunior.syncclip.server.app.plugins.ServerJson
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.AuthResponse
import io.github.slavikjunior.syncclip.server.contracts.dto.sync.PendingResponse
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
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Приёмка WS/очереди (§14.3): relay, pending-first-frame, ACK, анти-спуфинг,
 * размер payload, rate limit, `/sync/pending`.
 */
class SyncApiTest {

    private lateinit var database: DatabaseFactory
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }

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
    fun `pending rejects foreign device id`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        val http = jsonClient()
        val owner = registerAccount(http)
        val ownerDevice = owner.deviceId

        val response = http.get("/v1/sync/pending?deviceId=${IntegrationSupport.newDeviceId()}") {
            header(HttpHeaders.Authorization, "Bearer ${owner.tokens.accessToken}")
        }
        assertEquals(403, response.status.value)
        assertEquals("device_mismatch", response.body<io.github.slavikjunior.syncclip.server.contracts.error.ErrorResponse>().error)

        val valid = http.get("/v1/sync/pending?deviceId=$ownerDevice") {
            header(HttpHeaders.Authorization, "Bearer ${owner.tokens.accessToken}")
        }
        assertEquals(200, valid.status.value)
    }

    // ------------------------------------------------------------------ helpers

    private data class Account(
        val tokens: AuthResponse,
        val deviceId: String,
        val email: String,
        val password: String,
    )

    private suspend fun registerAccount(http: HttpClient): Account {
        val request = IntegrationSupport.registerRequest()
        val tokens = http.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<AuthResponse>()
        return Account(tokens, request.deviceId, request.email, request.password)
    }

    private suspend fun loginAs(http: HttpClient, owner: Account): Account {
        val deviceId = IntegrationSupport.newDeviceId()
        val tokens = http.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(
                IntegrationSupport.loginRequest(
                    email = owner.email,
                    password = owner.password,
                    deviceId = deviceId,
                ),
            )
        }.body<AuthResponse>()
        return Account(tokens, deviceId, owner.email, owner.password)
    }

    private fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
        install(ContentNegotiation) { json(ServerJson) }
    }

}
