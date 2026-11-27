package io.github.slavikjunior.syncclip.feature.settings.impl.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Контракт `/v1/devices`: конверт `{devices:[…]}` и имена полей `deviceId`/`osType`.
 *
 * Регрессия, которую ловит тест: раньше клиент ждал голый массив и падал с
 * `JsonConvertException` сразу после успешного входа.
 */
internal class KtorDeviceApiTest {

    private fun client(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun jsonHeaders() = headersOf(
        HttpHeaders.ContentType,
        ContentType.Application.Json.toString(),
    )

    @Test
    fun `devices list is decoded from envelope and mapped`() = runTest {
        var path: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            respond(
                content = """
                    {"devices":[
                      {"deviceId":"d1","name":"MacBook","icon":"LAPTOP","osType":"macOS",
                       "deviceType":"permanent","createdAt":"now","lastSeen":"now","isOnline":true}
                    ]}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val devices = KtorDeviceApi(client(engine)).getDevices().getOrThrow()

        assertEquals("/v1/devices", path)
        assertEquals(1, devices.size)
        assertEquals("d1", devices.single().id)
        assertEquals("macOS", devices.single().platform)
        assertEquals("LAPTOP", devices.single().icon)
    }

    @Test
    fun `updated device is unwrapped`() = runTest {
        var method: HttpMethod? = null
        val engine = MockEngine { request ->
            method = request.method
            respond(
                content = """
                    {"device":{"deviceId":"d1","name":"New","icon":"PHONE","osType":"Android",
                     "deviceType":"permanent","createdAt":"now","lastSeen":"now","isOnline":true}}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val updated = KtorDeviceApi(client(engine))
            .updateDevice("d1", name = "New", icon = "PHONE")
            .getOrThrow()

        assertEquals(HttpMethod.Patch, method)
        assertEquals("New", updated.name)
        assertEquals("Android", updated.platform)
    }
}
