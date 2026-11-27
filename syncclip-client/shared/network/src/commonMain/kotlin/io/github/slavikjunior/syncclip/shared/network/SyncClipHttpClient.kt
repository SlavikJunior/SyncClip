package io.github.slavikjunior.syncclip.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json

/**
 * Ручное управление рукопожатием: WS переподключает `SyncTransport`, а тело
 * upgrade-запроса не переотправляемо, поэтому авто-refresh его не трогает.
 */
internal val NO_AUTO_REFRESH: AttributeKey<Boolean> = AttributeKey("syncclip.no-auto-refresh")

/**
 * Общий Ktor-клиент. Движок подключается в platform-наборах `shared:network`
 * (OkHttp / CIO / Darwin), а вызывающий код передаёт только настройки.
 *
 * `401` обрабатывается один раз: [tokenRefresher] обновляет пару, запрос
 * повторяется с новым заголовком. Если refresh отвергнут — [onSessionExpired]
 * завершает сессию; при сетевой ошибке ответ `401` уходит вызывающему.
 */
public class SyncClipHttpClient(
    baseUrl: String,
    private val tokenProvider: () -> String?,
    private val tokenRefresher: TokenRefresher? = null,
    private val onSessionExpired: (suspend () -> Unit)? = null,
    engine: HttpClientEngine? = null,
) {
    public val baseUrl: String = baseUrl.trimEnd('/')

    public val client: HttpClient = if (engine == null) {
        HttpClient { configure() }
    } else {
        // Явный движок нужен тестам (MockEngine); в приложении — платформенный.
        HttpClient(engine) { configure() }
    }

    init {
        tokenRefresher?.let { refresher ->
            client.plugin(HttpSend).intercept { request ->
                val call = execute(request)

                // Обновляем только истёкший access, а не сам refresh и не
                // запросы без авторизации.
                if (call.response.status != HttpStatusCode.Unauthorized) return@intercept call
                if (request.url.build().encodedPath.endsWith(REFRESH_PATH)) return@intercept call
                if (request.attributes.getOrNull(NO_AUTO_REFRESH) == true) return@intercept call

                val staleToken = request.headers[HttpHeaders.Authorization]
                    ?.removePrefix(AUTHORIZATION_PREFIX)
                    ?.trim()

                when (refresher.refresh(staleToken)) {
                    TokenRefreshOutcome.REFRESHED -> {
                        request.headers.remove(HttpHeaders.Authorization)
                        tokenProvider()
                            ?.takeIf { it.isNotBlank() }
                            ?.let { token ->
                                request.headers.append(
                                    HttpHeaders.Authorization,
                                    "$AUTHORIZATION_PREFIX$token",
                                )
                            }
                        // Повтор ровно один: если и он 401, ошибка уходит наверх.
                        execute(request)
                    }

                    TokenRefreshOutcome.REJECTED -> {
                        onSessionExpired?.invoke()
                        call
                    }

                    TokenRefreshOutcome.UNAVAILABLE -> call
                }
            }
        }
    }

    private fun HttpClientConfig<*>.configure() {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
                contentType = ContentType.Application.Json,
            )
        }
        install(WebSockets) {
            // Сервер не должен присылать кадры больше полезной нагрузки паспорта
            // (512 КиБ + base64 + JSON). Без лимита скомпрометированный relay
            // или прокси может прислать кадр в сотни мегабайт, и клиент упадёт
            // по OOM до того, как применит проверку размера после расшифровки.
            maxFrameSize = MAX_FRAME_BYTES
        }
        install(Logging) {
            // Только метод/URL/статус: тела запросов не логируются (SEC-06).
            logger = object : Logger {
                override fun log(message: String) {
                    println("SyncClip HTTP: $message")
                }
            }
            level = LogLevel.INFO
        }
        defaultRequest {
            url(baseUrl)
            tokenProvider()
                ?.takeIf { it.isNotBlank() }
                ?.let { token -> header("Authorization", "Bearer $token") }
        }
    }

    /**
     * Открывает WebSocket к API.
     *
     * Токен ставится заголовком явно: сервер разрешает query-токен только при
     * `WS_ALLOW_QUERY_TOKEN=true`, а заголовок работает всегда и не попадает
     * в логи прокси.
     */
    public suspend fun openWebSocket(
        path: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ): DefaultClientWebSocketSession {
        val url = webSocketUrl(path)
        return client.webSocketSession {
            attributes.put(NO_AUTO_REFRESH, true)
            url(url)
            tokenProvider()
                ?.takeIf { it.isNotBlank() }
                ?.let { token -> header(HttpHeaders.Authorization, "Bearer $token") }
            extraHeaders.forEach { (name, value) -> header(name, value) }
        }
    }

    /** `http(s)` → `ws(s)`: тот же хост, другая схема. */
    public fun webSocketUrl(path: String): String {
        val scheme = when {
            baseUrl.startsWith("https://") -> "wss://"
            else -> "ws://"
        }
        val host = baseUrl.substringAfter("://")
        val normalizedPath = if (path.startsWith('/')) path else "/$path"
        return scheme + host + normalizedPath
    }

    public fun close() = client.close()

    private companion object {
        const val REFRESH_PATH = "/auth/refresh"
        const val AUTHORIZATION_PREFIX = "Bearer "

        /**
         * Тот же лимит, что у LAN-кадров: 512 КиБ нагрузки + base64 + JSON.
         * Держится в двух местах осознанно — модули не зависят друг от друга.
         */
        const val MAX_FRAME_BYTES = 1024L * 1024L
    }
}
