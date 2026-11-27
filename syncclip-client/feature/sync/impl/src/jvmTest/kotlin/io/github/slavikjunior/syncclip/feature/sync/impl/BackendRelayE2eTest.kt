package io.github.slavikjunior.syncclip.feature.sync.impl

import io.github.slavikjunior.syncclip.feature.sync.impl.data.remote.KtorSyncSocketFactory
import io.github.slavikjunior.syncclip.feature.sync.impl.data.transport.SyncConnectionStatus
import io.github.slavikjunior.syncclip.feature.sync.impl.data.transport.SyncTransport
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncPacket
import io.github.slavikjunior.syncclip.shared.crypto.AesGcmPayloadCipher
import io.github.slavikjunior.syncclip.shared.crypto.Pbkdf2
import io.github.slavikjunior.syncclip.shared.network.SyncClipHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * E2E против настоящего `syncclip-backend`.
 *
 * Запускается только при заданном `SYNCCLIP_E2E_URL` — без сервера тест
 * пропускается, а не падает (CI поднимает сервер отдельным шагом). Сценарий:
 * два устройства одного аккаунта, общий мастер-пароль, копирование на A →
 * доставка на B → ACK очищает офлайн-очередь.
 *
 * ```bash
 * SYNCCLIP_E2E_URL=http://localhost:18080 ./gradlew :feature:sync:impl:jvmTest \
 *   --tests '*BackendRelayE2eTest*'
 * ```
 */
internal class BackendRelayE2eTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `two devices sync through the real relay`() = runBlocking {
        val baseUrl = System.getenv("SYNCCLIP_E2E_URL") ?: return@runBlocking
        val http = HttpClient(CIO) {
            install(ContentNegotiation) { json(this@BackendRelayE2eTest.json) }
        }

        val email = "e2e-${Uuid.random()}@syncclip.local"
        val accountPassword = "e2e-account-password"
        val masterPassword = "e2e-master-password"
        val deviceA = Uuid.random().toString()
        val deviceB = Uuid.random().toString()

        val registered = http.post("$baseUrl/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                AuthRequest(
                    email = email,
                    password = accountPassword,
                    deviceId = deviceA,
                    deviceName = "E2E device A",
                    osType = "Linux",
                ),
            )
        }.body<AuthResponse>()

        val second = http.post("$baseUrl/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(
                AuthRequest(
                    email = email,
                    password = accountPassword,
                    deviceId = deviceB,
                    deviceName = "E2E device B",
                    osType = "Linux",
                ),
            )
        }.body<AuthResponse>()

        // Один мастер-пароль на аккаунт → одинаковый ключ на обоих устройствах.
        val key = Pbkdf2.deriveKey(masterPassword, salt = registered.userId)
        val cipher = AesGcmPayloadCipher()
        val outgoingA = FakePacketSource()
        val sinkB = FakeIncomingSink()
        val sinkA = FakeIncomingSink()

        val httpA = SyncClipHttpClient(baseUrl, tokenProvider = { registered.accessToken })
        val httpB = SyncClipHttpClient(baseUrl, tokenProvider = { second.accessToken })
        val transportA = transport(deviceA, outgoingA, sinkA, key, cipher, httpA)
        val transportB = transport(deviceB, FakePacketSource(), sinkB, key, cipher, httpB)

        val jobA = launch(Dispatchers.Default) { transportA.run() }
        val jobB = launch(Dispatchers.Default) { transportB.run() }

        try {
            waitUntil("оба клиента в сети") {
                transportA.connectionStatus.value == SyncConnectionStatus.ONLINE &&
                    transportB.connectionStatus.value == SyncConnectionStatus.ONLINE
            }
            delay(300)

            val text = "real backend e2e ${Uuid.random()}"
            outgoingA.publish(SyncPacket(eventId = Uuid.random().toString(), text = text, copiedAtMillis = System.currentTimeMillis()))

            waitUntil("B получил текст через сервер") { sinkB.received.isNotEmpty() }
            assertEquals(text, sinkB.received.first().text)
            assertEquals(deviceA, sinkB.received.first().originDeviceId)
            assertTrue(sinkA.received.isEmpty(), "своё событие не должно вернуться на A")

            // ACK удаляет пакет из офлайн-очереди: pending должен опустеть.
            waitUntil("очередь очищена ACK") {
                pendingCount(http, baseUrl, second.accessToken, deviceB) == 0
            }
            assertEquals(0, pendingCount(http, baseUrl, second.accessToken, deviceB))
        } finally {
            jobA.cancelAndJoin()
            jobB.cancelAndJoin()
            httpA.close()
            httpB.close()
            http.close()
        }
    }

    private fun transport(
        deviceId: String,
        outgoing: FakePacketSource,
        sink: FakeIncomingSink,
        key: ByteArray,
        cipher: AesGcmPayloadCipher,
        http: SyncClipHttpClient,
    ): SyncTransport = SyncTransport(
        socketFactory = KtorSyncSocketFactory(http) { deviceId },
        keyProvider = FakeKeyProvider(key),
        identity = FakeDeviceIdentityStore(deviceId),
        serverPackets = outgoing,
        incomingSink = sink,
        settings = FakeClipboardSettingsStore(),
        cipher = cipher,
        clock = io.github.slavikjunior.syncclip.core.clipboard.EpochClock.System,
    )

    private suspend fun pendingCount(
        http: HttpClient,
        baseUrl: String,
        token: String,
        deviceId: String,
    ): Int = http.get("$baseUrl/v1/sync/pending?deviceId=$deviceId&lastEventId=") {
        header(HttpHeaders.Authorization, "Bearer $token")
    }.body<PendingResponse>().totalCount

    private suspend fun waitUntil(message: String, condition: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            delay(POLL_MILLIS)
        }
        fail("$message: не дождались за $TIMEOUT_MILLIS мс")
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
        const val POLL_MILLIS = 50L
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

    @Serializable
    private data class PendingResponse(
        val packets: List<Packet> = emptyList(),
        val totalCount: Int = 0,
    )

    @Serializable
    private data class Packet(
        val eventId: String,
        val originDeviceId: String,
        val encryptedPayload: String,
        val payloadHash: String,
        val timestamp: Long,
    )
}
