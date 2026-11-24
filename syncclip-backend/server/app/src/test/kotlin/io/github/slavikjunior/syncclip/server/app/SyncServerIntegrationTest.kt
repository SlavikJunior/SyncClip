package io.github.slavikjunior.syncclip.server.app

import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * WS проверяется на реальном Netty-сервере (JDK HttpClient), а не на test-host:
 * Ktor test engine + runTest не диспатчит корутину WS-хендлера на другой
 * диспетчер, из-за чего `withContext` в транзакции зависает. Продовый путь
 * (Netty) проверяется этим тестом и scripts/e2e_smoke.py.
 */
class SyncServerIntegrationTest {

    private lateinit var database: DatabaseFactory
    private lateinit var server: EmbeddedServer<*, *>
    private var port: Int = 0
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }

    @BeforeEach
    fun setUp() {
        IntegrationSupport.assumeDatabase()
        database = IntegrationSupport.database()
        IntegrationSupport.truncate(database)
        server = embeddedServer(Netty, port = 0, host = "127.0.0.1") {
            module(IntegrationSupport.config(), database)
        }
        server.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        Assumptions.assumeTrue(port > 0)
    }

    @AfterEach
    fun tearDown() {
        if (::server.isInitialized) server.stop(200, 1_000)
        // ApplicationStopped закрывает пул; повторное закрытие безопасно.
        if (::database.isInitialized) runCatching { database.close() }
    }

    @Test
    fun `relays event between online devices and pending survives reconnect`() {
        val email = "ws-${UUID.randomUUID()}@example.com"
        val deviceA = UUID.randomUUID().toString()
        val deviceB = UUID.randomUUID().toString()
        val (tokenA, _) = register(email, deviceA)
        val (tokenB, _) = login(email, deviceB)

        val socketA = connect(deviceA, tokenA)
        val socketB = connect(deviceB, tokenB)

        val eventId = UUID.randomUUID().toString()
        socketA.sendText(eventPayload(deviceA, deviceB, eventId))
        val relayed = socketB.nextMessage()
        assertEquals("SYNC_EVENT", relayed["type"]?.jsonPrimitive?.content)
        assertEquals(eventId, relayed["eventId"]?.jsonPrimitive?.content)

        socketB.sendText("""{"type":"ACK","eventId":"$eventId","deviceId":"$deviceB"}""")
        Thread.sleep(300)
        assertEquals(0, pendingCount(deviceB, tokenB))

        // Офлайн: событие остаётся в очереди и приходит первым кадром после реконнекта.
        socketB.sendClose(WebSocket.NORMAL_CLOSURE, "bye").toCompletableFuture().get(5, TimeUnit.SECONDS)
        val offlineEvent = UUID.randomUUID().toString()
        socketA.sendText(eventPayload(deviceA, deviceB, offlineEvent))
        Thread.sleep(400)
        assertTrue(pendingCount(deviceB, tokenB) >= 1)

        val reconnected = connect(deviceB, tokenB)
        val first = reconnected.nextMessage()
        assertEquals("SYNC_EVENT", first["type"]?.jsonPrimitive?.content)

        socketA.abort()
        reconnected.abort()
    }

    @Test
    fun `rejects spoofed origin and keeps the connection alive`() {
        val email = "ws-${UUID.randomUUID()}@example.com"
        val deviceA = UUID.randomUUID().toString()
        val deviceB = UUID.randomUUID().toString()
        val (tokenA, _) = register(email, deviceA)
        login(email, deviceB)

        val socketA = connect(deviceA, tokenA)
        val spoofed = eventPayload(UUID.randomUUID().toString(), deviceB, UUID.randomUUID().toString())
        socketA.sendText(spoofed)

        val error = socketA.nextMessage()
        assertEquals("ERROR", error["type"]?.jsonPrimitive?.content)
        assertEquals("INVALID_PACKET", error["code"]?.jsonPrimitive?.content)

        socketA.sendText("""{"type":"PING","timestamp":777}""")
        val pong = socketA.nextMessage()
        assertEquals("PONG", pong["type"]?.jsonPrimitive?.content)
        assertEquals(777L, pong["timestamp"]?.jsonPrimitive?.content?.toLong())
        socketA.abort()
    }

    @Test
    fun `invalid event id returns ERROR without closing the socket`() {
        val email = "ws-${UUID.randomUUID()}@example.com"
        val deviceA = UUID.randomUUID().toString()
        val (tokenA, _) = register(email, deviceA)
        val socketA = connect(deviceA, tokenA)

        val payload = Base64.getEncoder().encodeToString(ByteArray(64) { 3 })
        socketA.sendText(
            """
            {"type":"SYNC_EVENT","eventId":"not-a-uuid","originDeviceId":"$deviceA",
             "targetDeviceIds":[],
             "encryptedPayload":"$payload",
             "payloadHash":"${"a".repeat(64)}","timestamp":${System.currentTimeMillis()}}
            """.trimIndent(),
        )
        val error = socketA.nextMessage()
        assertEquals("ERROR", error["type"]?.jsonPrimitive?.content)
        assertEquals("INVALID_PACKET", error["code"]?.jsonPrimitive?.content)

        // Один кривой пакет не рвёт соединение (FUNC-04).
        socketA.sendText("""{"type":"PING","timestamp":42}""")
        assertEquals("PONG", socketA.nextMessage()["type"]?.jsonPrimitive?.content)
        socketA.abort()
    }

    @Test
    fun `pending anchor works for every target of a fan-out event`() {
        val email = "ws-${UUID.randomUUID()}@example.com"
        val deviceA = UUID.randomUUID().toString()
        val deviceB = UUID.randomUUID().toString()
        val deviceC = UUID.randomUUID().toString()
        val (tokenA, _) = register(email, deviceA)
        val (tokenB, _) = login(email, deviceB)
        val (tokenC, _) = login(email, deviceC)

        val socketA = connect(deviceA, tokenA)
        val socketB = connect(deviceB, tokenB)
        val socketC = connect(deviceC, tokenC)

        val eventId = UUID.randomUUID().toString()
        val payload = Base64.getEncoder().encodeToString(ByteArray(64) { 5 })
        socketA.sendText(
            """
            {"type":"SYNC_EVENT","eventId":"$eventId","originDeviceId":"$deviceA",
             "targetDeviceIds":["$deviceB","$deviceC"],
             "encryptedPayload":"$payload",
             "payloadHash":"${"a".repeat(64)}","timestamp":${System.currentTimeMillis()}}
            """.trimIndent(),
        )
        assertEquals(eventId, socketB.nextMessage()["eventId"]?.jsonPrimitive?.content)
        assertEquals(eventId, socketC.nextMessage()["eventId"]?.jsonPrimitive?.content)

        // Fan-out: anchor того же eventId для C не должен считаться чужим (FUNC-02).
        val response = pending(deviceC, tokenC, lastEventId = eventId)
        assertEquals(200, response.statusCode(), response.body())
        val total = json.parseToJsonElement(response.body()).jsonObject["totalCount"]!!.jsonPrimitive.content.toInt()
        assertEquals(0, total)

        // B ACK-нул свою строку; повторный lastEventId не должен давать 400 (FUNC-05).
        socketB.sendText("""{"type":"ACK","eventId":"$eventId","deviceId":"$deviceB"}""")
        Thread.sleep(300)
        val afterAck = pending(deviceB, tokenB, lastEventId = eventId)
        assertEquals(200, afterAck.statusCode(), afterAck.body())

        socketA.abort()
        socketB.abort()
        socketC.abort()
    }

    @Test
    fun `acked event id anchors pending after the watermark`() {
        val email = "ws-${UUID.randomUUID()}@example.com"
        val deviceA = UUID.randomUUID().toString()
        val deviceB = UUID.randomUUID().toString()
        val (tokenA, _) = register(email, deviceA)
        val (tokenB, _) = login(email, deviceB)

        val socketA = connect(deviceA, tokenA)
        val socketB = connect(deviceB, tokenB)

        val oldEvent = UUID.randomUUID().toString()
        val newEvent = UUID.randomUUID().toString()
        socketA.sendText(eventPayload(deviceA, deviceB, oldEvent))
        socketA.sendText(eventPayload(deviceA, deviceB, newEvent))
        assertEquals(oldEvent, socketB.nextMessage()["eventId"]?.jsonPrimitive?.content)
        assertEquals(newEvent, socketB.nextMessage()["eventId"]?.jsonPrimitive?.content)

        // ACK только нового пакета: старый остаётся в очереди.
        socketB.sendText("""{"type":"ACK","eventId":"$newEvent","deviceId":"$deviceB"}""")
        Thread.sleep(300)

        val afterAck = pending(deviceB, tokenB, lastEventId = newEvent)
        assertEquals(200, afterAck.statusCode(), afterAck.body())
        val total = json.parseToJsonElement(afterAck.body()).jsonObject["totalCount"]!!.jsonPrimitive.content.toInt()
        assertEquals(0, total, "watermark должен скрыть более старый пакет")
        assertEquals(1, pendingCount(deviceB, tokenB), "без lastEventId старый пакет остаётся в очереди")

        socketA.abort()
        socketB.abort()
    }

    @Test
    fun `last event id from another account is rejected as foreign`() {
        val emailA = "ws-${UUID.randomUUID()}@example.com"
        val deviceA = UUID.randomUUID().toString()
        val deviceB = UUID.randomUUID().toString()
        val (tokenA, _) = register(emailA, deviceA)
        val (tokenB, _) = login(emailA, deviceB)

        val socketA = connect(deviceA, tokenA)
        val socketB = connect(deviceB, tokenB)
        val eventId = UUID.randomUUID().toString()
        socketA.sendText(eventPayload(deviceA, deviceB, eventId))
        assertEquals(eventId, socketB.nextMessage()["eventId"]?.jsonPrimitive?.content)

        // Чужой аккаунт не должен использовать eventId как anchor (CG-23).
        val otherEmail = "ws-${UUID.randomUUID()}@example.com"
        val otherDevice = UUID.randomUUID().toString()
        val (otherToken, _) = register(otherEmail, otherDevice)
        val response = pending(otherDevice, otherToken, lastEventId = eventId)
        assertEquals(400, response.statusCode(), response.body())
        assertTrue(response.body().contains("foreign_event"), response.body())

        socketA.abort()
        socketB.abort()
    }

    @Test
    fun `websocket connection limit per device is enforced`() {
        val email = "ws-${UUID.randomUUID()}@example.com"
        val deviceA = UUID.randomUUID().toString()
        val (tokenA, _) = register(email, deviceA)

        val sockets = (1..3).map { connect(deviceA, tokenA) }
        val fourth = connect(deviceA, tokenA)

        assertEquals(1008, fourth.awaitCloseCode())
        sockets.forEach { it.abort() }
    }

    @Test
    fun `websocket rate limit is shared per device across connections`() {
        val email = "ws-${UUID.randomUUID()}@example.com"
        val deviceA = UUID.randomUUID().toString()
        val (tokenA, _) = register(email, deviceA)
        val first = connect(deviceA, tokenA)
        val second = connect(deviceA, tokenA)

        val payload = Base64.getEncoder().encodeToString(ByteArray(64) { 7 })
        fun event() = """
            {"type":"SYNC_EVENT","eventId":"${UUID.randomUUID()}","originDeviceId":"$deviceA",
             "targetDeviceIds":[],
             "encryptedPayload":"$payload",
             "payloadHash":"${"a".repeat(64)}","timestamp":${System.currentTimeMillis()}}
        """.trimIndent()

        // rate limit на устройство: 5/мин в тестовом конфиге.
        repeat(5) { first.sendText(event()); Thread.sleep(20) }
        second.sendText(event())

        val error = second.nextMessage()
        assertEquals("ERROR", error["type"]?.jsonPrimitive?.content)
        assertEquals("RATE_LIMIT_EXCEEDED", error["code"]?.jsonPrimitive?.content)

        first.abort()
        second.abort()
    }

    // ------------------------------------------------------------------ helpers

    private fun register(email: String, deviceId: String): Pair<String, String> {
        val body = json.encodeToString(
            io.github.slavikjunior.syncclip.server.contracts.dto.auth.RegisterRequest.serializer(),
            io.github.slavikjunior.syncclip.server.contracts.dto.auth.RegisterRequest(
                email = email,
                password = "SecurePassword123!",
                deviceId = deviceId,
                deviceName = "WS Test A",
                osType = "macOS",
            ),
        )
        val response = post("/v1/auth/register", body)
        assertEquals(201, response.statusCode(), response.body())
        val parsed = json.parseToJsonElement(response.body()).jsonObject
        return parsed["accessToken"]!!.jsonPrimitive.content to parsed["refreshToken"]!!.jsonPrimitive.content
    }

    private fun login(email: String, deviceId: String): Pair<String, String> {
        val body = json.encodeToString(
            io.github.slavikjunior.syncclip.server.contracts.dto.auth.LoginRequest.serializer(),
            io.github.slavikjunior.syncclip.server.contracts.dto.auth.LoginRequest(
                email = email,
                password = "SecurePassword123!",
                deviceId = deviceId,
                deviceName = "WS Test B",
                osType = "Android",
            ),
        )
        val response = post("/v1/auth/login", body)
        assertEquals(200, response.statusCode(), response.body())
        val parsed = json.parseToJsonElement(response.body()).jsonObject
        return parsed["accessToken"]!!.jsonPrimitive.content to parsed["refreshToken"]!!.jsonPrimitive.content
    }

    private fun post(path: String, body: String): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun pendingCount(deviceId: String, token: String): Int {
        val response = pending(deviceId, token)
        assertEquals(200, response.statusCode(), response.body())
        return json.parseToJsonElement(response.body()).jsonObject["totalCount"]!!.jsonPrimitive.content.toInt()
    }

    private fun pending(deviceId: String, token: String, lastEventId: String? = null): HttpResponse<String> {
        val suffix = lastEventId?.let { "&lastEventId=$it" }.orEmpty()
        return http.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/v1/sync/pending?deviceId=$deviceId$suffix"))
                .header("Authorization", "Bearer $token")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    private fun connect(deviceId: String, token: String): JsonSocket {
        val listener = JsonSocket()
        val socket = http.newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .header("Authorization", "Bearer $token")
            .buildAsync(URI("ws://127.0.0.1:$port/v1/sync?deviceId=$deviceId"), listener)
            .get(10, TimeUnit.SECONDS)
        listener.attach(socket)
        return listener
    }

    private fun eventPayload(origin: String, target: String, eventId: String): String {
        val payload = Base64.getEncoder().encodeToString(ByteArray(64) { 3 })
        return """
            {"type":"SYNC_EVENT","eventId":"$eventId","originDeviceId":"$origin",
             "targetDeviceIds":["$target"],
             "encryptedPayload":"$payload",
             "payloadHash":"${"a".repeat(64)}","timestamp":${System.currentTimeMillis()}}
        """.trimIndent()
    }

    /** Listener, собирающий текстовые кадры в очередь; частичные кадры склеиваются. */
    private class JsonSocket : WebSocket.Listener {
        private val messages = LinkedBlockingQueue<String>()
        private val closeCodes = LinkedBlockingQueue<Int>()
        private val buffer = StringBuilder()
        private var socket: WebSocket? = null

        fun attach(webSocket: WebSocket) {
            socket = webSocket
        }

        override fun onOpen(webSocket: WebSocket) {
            socket = webSocket
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                messages.offer(buffer.toString())
                buffer.setLength(0)
            }
            webSocket.request(1)
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            messages.offer("""{"type":"TRANSPORT_ERROR","message":"${error.javaClass.simpleName}"}""")
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            closeCodes.offer(statusCode)
            return null
        }

        fun awaitCloseCode(timeoutSeconds: Long = 10): Int? =
            closeCodes.poll(timeoutSeconds, TimeUnit.SECONDS)

        fun nextMessage(timeoutSeconds: Long = 10): kotlinx.serialization.json.JsonObject {
            val raw = messages.poll(timeoutSeconds, TimeUnit.SECONDS)
            assertNotNull(raw, "no ws frame within ${timeoutSeconds}s")
            return Json { ignoreUnknownKeys = true }.parseToJsonElement(raw).jsonObject
        }

        fun sendText(text: String) {
            val ws = assertNotNull(socket, "socket is not attached")
            ws.sendText(text, true).join()
        }

        fun sendClose(code: Int = 1000, reason: String = ""): CompletionStage<WebSocket> {
            val ws = assertNotNull(socket, "socket is not attached")
            return ws.sendClose(code, reason)
        }

        fun abort() {
            socket?.abort()
        }
    }
}
