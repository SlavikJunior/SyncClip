package io.github.slavikjunior.syncclip.feature.sync.impl.data.remote

import io.github.slavikjunior.syncclip.core.datastore.E2eeLockoutStore
import io.github.slavikjunior.syncclip.core.datastore.MasterPasswordLockout
import io.github.slavikjunior.syncclip.core.datastore.SessionSnapshot
import io.github.slavikjunior.syncclip.core.datastore.SessionState
import io.github.slavikjunior.syncclip.core.datastore.SessionStore
import io.github.slavikjunior.syncclip.feature.sync.api.E2eeUnlockResult
import io.github.slavikjunior.syncclip.shared.crypto.AesGcmPayloadCipher
import io.github.slavikjunior.syncclip.shared.crypto.Pbkdf2
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Проверка canary-флоу (FR-1.3) без сервера: MockEngine отвечает по контракту
 * `docs/API_SPEC.yaml`, а ключ выводится настоящим PBKDF2 (с малым числом
 * итераций ради скорости теста).
 */
internal class KtorE2eeKeyProvisionerTest {

    private val userId = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"
    private val cipher = AesGcmPayloadCipher()
    private val iterations = 10
    private val session = FakeSessionStore(userId)

    @Test
    fun `existing canary accepts the right password`() = runTest {
        val canary = encryptCanary("right-password")
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/v1/auth/canary" -> respondJson("""{"encryptedCanary":"$canary"}""")
                "/v1/auth/canary/attempt" -> respondJson("""{"remainingAttempts":3}""")
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val result = provisioner(engine).unlock("right-password")

        val success = assertIs<E2eeUnlockResult.Success>(result)
        assertEquals(32, success.key.size)
        assertEquals(
            Pbkdf2.deriveKey("right-password", userId, iterations).toList(),
            success.key.toList(),
        )
    }

    @Test
    fun `wrong password is rejected by canary`() = runTest {
        val canary = encryptCanary("right-password")
        val attempts = mutableListOf<Boolean>()
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/v1/auth/canary" -> respondJson("""{"encryptedCanary":"$canary"}""")
                "/v1/auth/canary/attempt" -> {
                    attempts += (request.body as TextContent).text.contains("false")
                    respondJson("""{"remainingAttempts":2}""")
                }

                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val lockout = FakeLockoutStore()
        val result = provisioner(engine, lockout = lockout).unlock("wrong-password")

        val rejection = assertIs<E2eeUnlockResult.InvalidPassword>(result)
        assertEquals(2, rejection.remainingAttempts)
        assertEquals(2, lockout.state.value.remainingAttempts)
        assertEquals(listOf(true), attempts, "неудача должна попасть в лимиты сервера")
    }

    @Test
    fun `missing canary is created on first device`() = runTest {
        var posted: String? = null
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/v1/auth/canary" -> if (request.method.value == "GET") {
                    respond("""{"error":"canary_not_found"}""", HttpStatusCode.NotFound)
                } else {
                    posted = (request.body as TextContent).text
                    respond("""{"success":true}""", HttpStatusCode.Created)
                }

                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val result = provisioner(engine).unlock("fresh-password")

        val success = assertIs<E2eeUnlockResult.Success>(result)
        val body = assertNotNull(posted)
        val encrypted = Json.parseToJsonElement(body)
            .jsonObject["encryptedCanary"]!!
            .jsonPrimitive
            .content
        val canary = cipher.decrypt(encrypted, success.key)
        assertTrue(canary.startsWith("syncclip-canary-"), "префикс canary обязателен")
    }

    @Test
    fun `network failure is reported as unavailable`() = runTest {
        val engine = MockEngine { throw IllegalStateException("offline") }

        val result = provisioner(engine).unlock("any-password")

        assertIs<E2eeUnlockResult.Unavailable>(result)
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun provisioner(
        engine: MockEngine,
        lockout: FakeLockoutStore? = null,
        clock: () -> Long = { 1_000_000L },
    ): KtorE2eeKeyProvisioner {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }
        return KtorE2eeKeyProvisioner(
            client = client,
            session = session,
            cipher = cipher,
            lockout = lockout,
            iterations = iterations,
            clock = clock,
        )
    }

    private fun encryptCanary(password: String): String {
        val key = Pbkdf2.deriveKey(password, userId, iterations)
        return cipher.encrypt("syncclip-canary-test", key)
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(body: String) =
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

    @Test
    fun `third failure locks input and blocks further requests`() = runTest {
        val canary = encryptCanary("right-password")
        var requests = 0
        val plans = ArrayDeque(
            listOf(
                HttpStatusCode.OK to """{"remainingAttempts":2}""",
                HttpStatusCode.OK to """{"remainingAttempts":1}""",
                HttpStatusCode.Forbidden to """{"error":"master_password_locked","retryAfter":300}""",
            ),
        )
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/v1/auth/canary" -> {
                    requests++
                    respondJson("""{"encryptedCanary":"$canary"}""")
                }

                "/v1/auth/canary/attempt" -> {
                    requests++
                    val (status, body) = plans.removeFirst()
                    respond(
                        content = body,
                        status = status,
                        headers = if (status == HttpStatusCode.Forbidden) {
                            headersOf(HttpHeaders.RetryAfter, "300")
                        } else {
                            jsonHeaders()
                        },
                    )
                }

                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val lockout = FakeLockoutStore()
        var now = 1_000_000L
        val provisioner = provisioner(engine, lockout) { now }

        assertIs<E2eeUnlockResult.InvalidPassword>(provisioner.unlock("wrong"))
        assertIs<E2eeUnlockResult.InvalidPassword>(provisioner.unlock("wrong"))
        val locked = assertIs<E2eeUnlockResult.Locked>(provisioner.unlock("wrong"))
        assertEquals(300, locked.retryAfterSeconds)
        assertEquals(now + 300_000, lockout.current().lockedUntilMillis)

        val before = requests
        val stillLocked = assertIs<E2eeUnlockResult.Locked>(provisioner.unlock("any"))
        assertEquals(300, stillLocked.retryAfterSeconds)
        assertEquals(before, requests, "при активной блокировке запросы не отправляются")

        now += 300_001
        val afterExpiry = provisioner.unlock("right-password")
        assertIs<E2eeUnlockResult.Success>(afterExpiry)
        assertEquals(MasterPasswordLockout(), lockout.current(), "успех сбрасывает блокировку")
    }

    @Test
    fun `lockout cycle is 5m, 5m, 2h`() = runTest {
        val canary = encryptCanary("right-password")
        val plans = ArrayDeque(
            listOf(
                HttpStatusCode.OK to """{"remainingAttempts":2}""",
                HttpStatusCode.OK to """{"remainingAttempts":1}""",
                HttpStatusCode.Forbidden to """{"error":"master_password_locked","retryAfter":300}""",
                HttpStatusCode.OK to """{"remainingAttempts":2}""",
                HttpStatusCode.OK to """{"remainingAttempts":1}""",
                HttpStatusCode.Forbidden to """{"error":"master_password_locked","retryAfter":300}""",
                HttpStatusCode.OK to """{"remainingAttempts":2}""",
                HttpStatusCode.OK to """{"remainingAttempts":1}""",
                // Третий цикл: тело без Retry-After, только заголовок не нужен — 7200 в теле.
                HttpStatusCode.Forbidden to """{"error":"master_password_locked","retryAfter":7200}""",
            ),
        )
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/v1/auth/canary" -> respondJson("""{"encryptedCanary":"$canary"}""")
                "/v1/auth/canary/attempt" -> {
                    val (status, body) = plans.removeFirst()
                    respond(
                        content = body,
                        status = status,
                        headers = if (status == HttpStatusCode.Forbidden) {
                            jsonHeaders()
                        } else {
                            jsonHeaders()
                        },
                    )
                }

                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val lockout = FakeLockoutStore()
        var now = 1_000_000L
        val provisioner = provisioner(engine, lockout) { now }

        suspend fun failGroup(): Long {
            assertIs<E2eeUnlockResult.InvalidPassword>(provisioner.unlock("wrong"))
            assertIs<E2eeUnlockResult.InvalidPassword>(provisioner.unlock("wrong"))
            return assertIs<E2eeUnlockResult.Locked>(provisioner.unlock("wrong")).retryAfterSeconds
        }

        assertEquals(300, failGroup())
        now += 300_001
        assertEquals(300, failGroup())
        now += 300_001
        assertEquals(7200, failGroup())
    }

    @Test
    fun `successful unlock clears previous attempts`() = runTest {
        val canary = encryptCanary("right-password")
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/v1/auth/canary" -> respondJson("""{"encryptedCanary":"$canary"}""")
                "/v1/auth/canary/attempt" -> respondJson("""{"remainingAttempts":3}""")
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val lockout = FakeLockoutStore().apply {
            recordAttempt(1)
        }

        assertIs<E2eeUnlockResult.Success>(provisioner(engine, lockout).unlock("right-password"))
        assertEquals(MasterPasswordLockout(), lockout.current())
    }

/** Управляемая блокировка для тестов провижионера. */
private class FakeLockoutStore : E2eeLockoutStore {

    private val values = MutableStateFlow(MasterPasswordLockout())
    override val state: StateFlow<MasterPasswordLockout> = values

    override suspend fun current(): MasterPasswordLockout = values.value

    override suspend fun recordAttempt(remaining: Int) {
        values.value = MasterPasswordLockout(remainingAttempts = remaining)
    }

    override suspend fun lockUntil(lockedUntilMillis: Long) {
        values.value = MasterPasswordLockout(
            remainingAttempts = 0,
            lockedUntilMillis = lockedUntilMillis,
        )
    }

    override suspend fun clear() {
        values.value = MasterPasswordLockout()
    }
}
}

/** Сессия без DataStore: provisioner нужен только `userId`. */
private class FakeSessionStore(
    userId: String,
) : SessionStore {

    private val mutableState = MutableStateFlow<SessionState>(
        SessionState.SignedIn(
            SessionSnapshot(
                userId = userId,
                accessToken = "access",
                refreshToken = "refresh",
            ),
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

    override fun accessToken(): String? = "access"
}
