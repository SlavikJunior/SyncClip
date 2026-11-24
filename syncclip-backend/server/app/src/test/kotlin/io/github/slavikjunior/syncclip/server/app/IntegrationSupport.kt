package io.github.slavikjunior.syncclip.server.app

import io.github.slavikjunior.syncclip.server.app.plugins.ServerJson
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.AuthResponse
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.LoginRequest
import io.github.slavikjunior.syncclip.server.contracts.dto.auth.RegisterRequest
import io.github.slavikjunior.syncclip.server.core.config.AppConfig
import io.github.slavikjunior.syncclip.server.core.config.ConfigLoader
import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import org.junit.jupiter.api.Assumptions
import java.util.UUID

/**
 * Интеграционные тесты идут на реальном PostgreSQL (TEST_DB_URL) — как в §14.2:
 * без Testcontainers, через переменную окружения/service container в CI.
 */
internal object IntegrationSupport {

    private val url: String? = System.getenv("TEST_DB_URL")
    private val user: String = System.getenv("TEST_DB_USER") ?: "postgres"
    private val password: String = System.getenv("TEST_DB_PASSWORD") ?: "test"

    fun assumeDatabase(): Boolean {
        val available = url != null && runCatching {
            DatabaseFactory(config().database).use { it.isReady() }
        }.getOrDefault(false)
        Assumptions.assumeTrue(available, "TEST_DB_URL is not set or PostgreSQL is unavailable")
        return available
    }

    fun config(overrides: Map<String, String> = emptyMap()): AppConfig = ConfigLoader.load(
        mapOf(
            "DB_URL" to (url ?: "jdbc:postgresql://localhost:55433/syncclip_test"),
            "DB_USER" to user,
            "DB_PASSWORD" to password,
            "DB_POOL_SIZE" to "20",
            "JWT_SECRET" to "integration-test-secret-integration-test-secret",
            "PASSWORD_HASH_ITERATIONS" to "10000",
            "PUSH_PROVIDER" to "noop",
            "LOG_JSON" to "false",
            "WS_OUTBOUND_CAPACITY" to "4",
            "RATE_LIMIT_LOGIN_PER_MINUTE" to "100",
            "RATE_LIMIT_REGISTER_PER_MINUTE" to "100",
            "RATE_LIMIT_REFRESH_PER_MINUTE" to "200",
            "RATE_LIMIT_CANARY_PER_MINUTE" to "100",
            "RATE_LIMIT_CANARY_ATTEMPT_PER_MINUTE" to "100",
            "RATE_LIMIT_GUEST_INVITE_PER_HOUR" to "100",
            "RATE_LIMIT_GUEST_EXCHANGE_PER_MINUTE" to "100",
            "RATE_LIMIT_DEFAULT_PER_MINUTE" to "1000",
            "RATE_LIMIT_PENDING_PER_MINUTE" to "100",
            "RATE_LIMIT_SYNC_EVENTS_PER_MINUTE" to "5",
            "RATE_LIMIT_ACKS_PER_MINUTE" to "10",
            "RATE_LIMIT_PING_PER_MINUTE" to "100",
        ) + overrides,
    )

    fun database(): DatabaseFactory = DatabaseFactory(config().database).also { it.migrate() }

    fun truncate(factory: DatabaseFactory) {
        factory.dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    TRUNCATE TABLE offline_queue, fcm_tokens, auth_attempts, master_password_locks,
                                   refresh_tokens, guest_invites, device_sync_state, encrypted_canary, devices, users
                    RESTART IDENTITY CASCADE
                    """.trimIndent(),
                )
            }
        }
    }

    fun newDeviceId(): String = UUID.randomUUID().toString()

    fun registerRequest(
        email: String = "user-${UUID.randomUUID()}@example.com",
        password: String = "SecurePassword123!",
        deviceId: String = newDeviceId(),
        deviceName: String = "Test Device",
        osType: String = "macOS",
    ) = RegisterRequest(email, password, deviceId, deviceName, osType)

    fun loginRequest(
        email: String,
        password: String = "SecurePassword123!",
        deviceId: String,
        deviceName: String = "Second Device",
        osType: String = "Android",
    ) = LoginRequest(email, password, deviceId, deviceName, osType)

    fun HttpClient.withJson(): HttpClient = config {
        install(ContentNegotiation) { json(ServerJson) }
    }
}

internal suspend fun HttpClient.register(request: RegisterRequest): HttpResponse =
    post("/v1/auth/register") {
        contentType(ContentType.Application.Json)
        setBody(request)
    }

internal suspend fun HttpClient.login(request: LoginRequest): HttpResponse =
    post("/v1/auth/login") {
        contentType(ContentType.Application.Json)
        setBody(request)
    }

internal suspend fun HttpResponse.authTokens(): AuthResponse = body()
