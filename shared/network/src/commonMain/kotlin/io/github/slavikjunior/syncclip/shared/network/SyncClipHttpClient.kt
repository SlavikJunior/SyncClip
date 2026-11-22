package io.github.slavikjunior.syncclip.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Общий Ktor-клиент. Движок подключается в platform-наборах `shared:network`
 * (OkHttp / CIO / Darwin), а вызывающий код передаёт только настройки.
 */
class SyncClipHttpClient(
    baseUrl: String,
    private val tokenProvider: () -> String,
) {
    val client: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
                contentType = ContentType.Application.Json,
            )
        }
        defaultRequest {
            url(baseUrl)
            header("Authorization", "Bearer ${tokenProvider()}")
        }
    }

    fun close() = client.close()
}
