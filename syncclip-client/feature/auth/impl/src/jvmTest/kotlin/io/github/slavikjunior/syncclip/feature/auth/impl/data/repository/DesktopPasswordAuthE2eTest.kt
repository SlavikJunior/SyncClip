package io.github.slavikjunior.syncclip.feature.auth.impl.data.repository

import io.github.slavikjunior.syncclip.core.datastore.DeviceIdentity
import io.github.slavikjunior.syncclip.core.datastore.DeviceIdentityStore
import io.github.slavikjunior.syncclip.core.firebase.FirebaseAuthFailure
import io.github.slavikjunior.syncclip.feature.auth.impl.data.remote.KtorAuthApi
import io.github.slavikjunior.syncclip.feature.auth.impl.domain.model.AuthResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * Desktop-аутентификация на настоящем сервере: регистрация и вход второго
 * устройства по email/паролю (Firebase на JVM вернул бы NOT_SUPPORTED).
 *
 * Запуск:
 * ```bash
 * SYNCCLIP_E2E_URL=http://localhost:18080 ./gradlew :feature:auth:impl:jvmTest \
 *   --tests '*DesktopPasswordAuthE2eTest*'
 * ```
 */
internal class DesktopPasswordAuthE2eTest {

    @Test
    fun `desktop signs up and second device signs in`() = runBlocking {
        val baseUrl = System.getenv("SYNCCLIP_E2E_URL") ?: return@runBlocking
        val client = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            // В приложении base URL задаёт SyncClipHttpClient; здесь — явно.
            defaultRequest { url(baseUrl) }
        }
        try {
            val api = KtorAuthApi(client)
            val email = "desktop-${Uuid.random()}@syncclip.local"
            val password = "desktop-password-123"

            val firstSession = FakeSessionStore()
            val first = repository(api, firstSession, Uuid.random().toString())
            assertEquals(AuthResult.Success, first.signUp(email, password), "регистрация Desktop")
            val userId = assertNotNull(firstSession.saved?.userId)

            val secondSession = FakeSessionStore()
            val second = repository(api, secondSession, Uuid.random().toString())
            assertEquals(AuthResult.Success, second.signIn(email, password), "вход второго устройства")
            assertEquals(userId, secondSession.saved?.userId, "оба устройства в одном аккаунте")
        } catch (error: IllegalStateException) {
            fail("Desktop auth e2e failed: ${error.message}")
        } finally {
            client.close()
        }
    }

    private fun repository(
        api: KtorAuthApi,
        session: FakeSessionStore,
        deviceId: String,
    ): FirebaseAuthRepository = FirebaseAuthRepository(
        gateway = FakeFirebaseAuthGateway(
            signInResult = firebaseFailure(FirebaseAuthFailure.NOT_SUPPORTED),
        ),
        api = api,
        sessionStore = session,
        deviceIdentityStore = object : DeviceIdentityStore {
            override suspend fun identity(): DeviceIdentity = DeviceIdentity(
                deviceId = deviceId,
                deviceName = "Desktop e2e",
                osType = "Linux",
            )
        },
        pushTokens = FakePushTokenSource(token = null),
    )
}
