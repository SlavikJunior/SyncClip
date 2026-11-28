package io.github.slavikjunior.syncclip.feature.sync.impl

import io.github.slavikjunior.syncclip.feature.sync.impl.data.remote.KtorSyncSocketFactory
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncConnectionStatus
import io.github.slavikjunior.syncclip.feature.sync.impl.data.transport.SyncTransport
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncPacket
import io.github.slavikjunior.syncclip.shared.crypto.AesGcmPayloadCipher
import io.github.slavikjunior.syncclip.shared.crypto.secureRandomBytes
import io.github.slavikjunior.syncclip.shared.network.SyncClipHttpClient
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * E2E двух клиентов через локальный relay: настоящий WebSocket, настоящий
 * AES-256-GCM, настоящие ACK.
 *
 * Сервер нарочно простой (пересылает кадры между сессиями): он проверяет
 * связку клиентов, а не серверную реализацию — та покрыта тестами в
 * `syncclip-backend`. Контракт кадров совпадает с `WsCodec`/контрактами сервера.
 */
internal class SyncTransportRelayE2eTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `two clients exchange encrypted clipboard through relay`() = runBlocking {
        val key = secureRandomBytes(32)
        val cipher = AesGcmPayloadCipher()
        val relay = RelayServer().also { it.start() }
        val outgoingA = FakePacketSource()
        val outgoingB = FakePacketSource()
        val sinkA = FakeIncomingSink()
        val sinkB = FakeIncomingSink()

        val httpA = SyncClipHttpClient("http://127.0.0.1:${relay.port}", tokenProvider = { null })
        val httpB = SyncClipHttpClient("http://127.0.0.1:${relay.port}", tokenProvider = { null })
        val transportA = transport("device-a", outgoingA, sinkA, key, cipher, httpA)
        val transportB = transport("device-b", outgoingB, sinkB, key, cipher, httpB)

        val jobA = launch(Dispatchers.Default) { transportA.run() }
        val jobB = launch(Dispatchers.Default) { transportB.run() }

        try {
            waitUntil("оба клиента подключились") {
                transportA.connectionStatus.value == SyncConnectionStatus.ONLINE &&
                    transportB.connectionStatus.value == SyncConnectionStatus.ONLINE
            }
            // Даём sendLoop обоих транспортов подписаться на исходящий поток:
            // online выставляется до запуска дочерних корутин.
            delay(300)

            outgoingA.publish(SyncPacket(eventId = "event-e2e", text = "hello over relay", copiedAtMillis = System.currentTimeMillis()))

            waitUntil("клиент B получил пакет") { sinkB.received.isNotEmpty() }
            assertEquals("hello over relay", sinkB.received.first().text)
            assertEquals("device-a", sinkB.received.first().originDeviceId)

            waitUntil("relay получил ACK от B") { relay.acks.get() >= 1 }
            assertTrue(relay.acks.get() >= 1, "клиент обязан подтвердить доставку")

            // Свое же событие вернулось в очередь — повторно не применяем.
            assertTrue(sinkA.received.isEmpty(), "эхо своего пакета не должно применяться")
        } finally {
            jobA.cancelAndJoin()
            jobB.cancelAndJoin()
            httpA.close()
            httpB.close()
            relay.stop()
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
        clock = TestClock(),
    )

    private suspend fun waitUntil(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            delay(POLL_MILLIS)
        }
        fail("$message: не дождались за $TIMEOUT_MILLIS мс")
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
    }

    /**
     * Минимальный relay: пересылает `SYNC_EVENT` другим сессиям, считает ACK,
     * отвечает на PING. Аутентификации нет — она проверяется на реальном сервере.
     */
    private inner class RelayServer {

        val sessions = ConcurrentHashMap<String, io.ktor.server.websocket.DefaultWebSocketServerSession>()
        val acks = AtomicInteger()
        val port: Int = ServerSocket(0).use { it.localPort }

        private lateinit var server: EmbeddedServer<*, *>

        fun start() {
            server = embeddedServer(CIO, port = port) {
                install(WebSockets)
                routing {
                    webSocket("/v1/sync") {
                        val deviceId = call.request.queryParameters["deviceId"] ?: return@webSocket
                        sessions[deviceId] = this
                        try {
                            for (frame in incoming) {
                                val text = (frame as? Frame.Text)?.readText() ?: continue
                                val body = json.parseToJsonElement(text).jsonObject
                                when (body["type"]?.jsonPrimitive?.content) {
                                    "SYNC_EVENT" -> sessions
                                        .filterKeys { it != deviceId }
                                        .values
                                        .forEach { peer -> peer.send(Frame.Text(text)) }

                                    "ACK" -> acks.incrementAndGet()

                                    "PING" -> send(
                                        Frame.Text(
                                            """{"type":"PONG","timestamp":${body["timestamp"]}}""",
                                        ),
                                    )
                                }
                            }
                        } finally {
                            sessions.remove(deviceId)
                        }
                    }
                }
            }.start(wait = false)
        }

        fun stop() {
            if (::server.isInitialized) {
                server.stop(gracePeriodMillis = 100, timeoutMillis = 1_000)
            }
            sessions.clear()
        }
    }
}
