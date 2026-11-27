package io.github.slavikjunior.syncclip.feature.sync.impl.data.remote

import io.github.slavikjunior.syncclip.core.datastore.E2eeLockoutStore
import io.github.slavikjunior.syncclip.core.datastore.MasterPasswordLockout
import io.github.slavikjunior.syncclip.core.datastore.SessionSnapshot
import io.github.slavikjunior.syncclip.core.datastore.SessionState
import io.github.slavikjunior.syncclip.core.datastore.SessionStore
import io.github.slavikjunior.syncclip.feature.sync.api.E2eeUnlockResult
import io.github.slavikjunior.syncclip.shared.crypto.AesGcmPayloadCipher
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
import kotlin.test.assertTrue

/**
 * FR-1.4 против настоящего сервера: третья неудача запирает ввод на 5 минут,
 * а клиент после этого не отправляет запросы до истечения таймера.
 *
 * ```bash
 * SYNCCLIP_E2E_URL=http://localhost:18080 ./gradlew :feature:sync:impl:jvmTest \
 *   --tests '*MasterPasswordLockoutE2eTest*'
 * ```
 */
internal class MasterPasswordLockoutE2eTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `third wrong master password locks the client locally`() = runBlocking {
        val baseUrl = System.getenv("SYNCCLIP_E2E_URL") ?: return@runBlocking
        val deviceId = Uuid.random().toString()
        val account = "lockout-${Uuid.random()}@syncclip.local"
        val accountPassword = "lockout-account-password"
        val masterPassword = "lockout-master-password"

        val bootstrap = HttpClient(CIO) {
            install(ContentNegotiation) { json(this@MasterPasswordLockoutE2eTest.json) }
        }
        val registered = try {
            bootstrap.post("$baseUrl/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(AuthRequest(account, accountPassword, deviceId, "Lockout e2e", "Linux"))
            }.body<AuthResponse>()
        } finally {
            bootstrap.close()
        }

        val client = HttpClient(CIO) {
            install(ContentNegotiation) { json(this@MasterPasswordLockoutE2eTest.json) }
            defaultRequest {
                url(baseUrl)
                header(HttpHeaders.Authorization, "Bearer ${registered.accessToken}")
            }
        }
        try {
            val lockout = FakeLockoutStore()
            var now = System.currentTimeMillis()
            val provisioner = KtorE2eeKeyProvisioner(
                client = client,
                session = StaticSession(registered.userId),
                cipher = AesGcmPayloadCipher(),
                lockout = lockout,
                iterations = 1_000,
                clock = { now },
            )

            // Первое устройство: canary ещё нет — enroll, ключ создаётся.
            val enrolled = provisioner.unlock(masterPassword)
            assertIs<E2eeUnlockResult.Success>(enrolled, "canary должен создаться на первом устройстве")

            // Две неудачи считаются сервером, третья — блокировка на 5 минут.
            val first = assertIs<E2eeUnlockResult.InvalidPassword>(provisioner.unlock("wrong-1"))
            assertEquals(2, first.remainingAttempts)
            val second = assertIs<E2eeUnlockResult.InvalidPassword>(provisioner.unlock("wrong-2"))
            assertEquals(1, second.remainingAttempts)

            val locked = assertIs<E2eeUnlockResult.Locked>(provisioner.unlock("wrong-3"))
            assertEquals(300, locked.retryAfterSeconds, "первая блокировка — 5 минут")
            val stored = lockout.current()
            assertTrue(stored.isLocked(now), "клиент обязан запомнить блокировку")
            assertEquals(300, stored.remainingSeconds(now))

            // Пока блокировка активна, даже верный пароль не отправляется на сервер.
            val blocked = assertIs<E2eeUnlockResult.Locked>(provisioner.unlock(masterPassword))
            assertEquals(300, blocked.retryAfterSeconds)

            // После истечения таймера верный пароль снова проходит и сбрасывает счётчик.
            now += 300_001
            assertIs<E2eeUnlockResult.Success>(provisioner.unlock(masterPassword))
            assertEquals(MasterPasswordLockout(), lockout.current())
        } finally {
            client.close()
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

/** Сессия с фиксированным `userId`: provisioner нужен только он. */
private class StaticSession(userId: String) : SessionStore {

    private val mutableState = MutableStateFlow<SessionState>(
        SessionState.SignedIn(
            SessionSnapshot(userId = userId, accessToken = "e2e", refreshToken = "e2e"),
        ),
    )

    override val state: StateFlow<SessionState> = mutableState

    override suspend fun refresh() = Unit

    override suspend fun save(session: SessionSnapshot) {
        mutableState.value = SessionState.SignedIn(session)
    }

    override suspend fun updateTokens(accessToken: String, refreshToken: String) = Unit

    override suspend fun clear() {
        mutableState.value = SessionState.SignedOut
    }

    override fun accessToken(): String? = "e2e"
}

/** Блокировка в памяти: e2e проверяет поведение, а не DataStore. */
private class FakeLockoutStore : E2eeLockoutStore {

    private val values = MutableStateFlow(MasterPasswordLockout())
    override val state: StateFlow<MasterPasswordLockout> = values

    override suspend fun current(): MasterPasswordLockout = values.value

    override suspend fun recordAttempt(remaining: Int) {
        values.value = MasterPasswordLockout(remainingAttempts = remaining)
    }

    override suspend fun lockUntil(lockedUntilMillis: Long) {
        values.value = MasterPasswordLockout(0, lockedUntilMillis)
    }

    override suspend fun clear() {
        values.value = MasterPasswordLockout()
    }
}
