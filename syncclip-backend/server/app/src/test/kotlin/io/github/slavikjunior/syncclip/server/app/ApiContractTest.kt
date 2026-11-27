package io.github.slavikjunior.syncclip.server.app

import io.github.slavikjunior.syncclip.server.contracts.ApiPaths
import org.yaml.snakeyaml.Yaml
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ARCH-04: контрактный тест по `docs/API_SPEC.yaml`.
 *
 * Проверяет, что реализованные REST-пути задокументированы (кроме известных
 * расширений из CONTRACT_GAPS), а SEC-10 не вернул `403 account_locked`
 * в контракт login.
 */
class ApiContractTest {

    private val spec: Map<String, Any> = run {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "docs/API_SPEC.yaml") }
            .firstOrNull { it.isFile }
            ?: error("docs/API_SPEC.yaml not found")
        @Suppress("UNCHECKED_CAST")
        Yaml().load<Map<String, Any>>(file.inputStream())
    }

    /**
     * Ключи в спецификации содержат префикс `/v1` (полный путь запроса),
     * `ApiPaths` — тоже, но сравнение идёт по нормализованному виду без него.
     */
    @Suppress("UNCHECKED_CAST")
    private val paths: Map<String, Any> =
        (spec["paths"] as Map<String, Any>).mapKeys { it.key.removePrefix(ApiPaths.V1) }

    /** Реализовано как расширение контракта (CG-02/04/08/21, WebSocket protocol). */
    private val extensionPaths = setOf(
        "/auth/logout",
        "/auth/guest/invite",
        "/auth/guest/exchange",
        "/auth/canary/attempt",
        "/notifications/unread",
        "/sync",
        "/health/live",
        "/health/ready",
    )

    @Test
    fun `all implemented REST paths are documented or explicitly listed as gaps`() {
        val implemented = listOf(
            ApiPaths.AUTH_REGISTER,
            ApiPaths.AUTH_LOGIN,
            ApiPaths.AUTH_REFRESH,
            ApiPaths.AUTH_CANARY,
            ApiPaths.AUTH_CANARY_ATTEMPT,
            ApiPaths.AUTH_GUEST_INVITE,
            ApiPaths.AUTH_GUEST_EXCHANGE,
            ApiPaths.AUTH_LOGOUT,
            ApiPaths.AUTH_FIREBASE,
            ApiPaths.SYNC_PENDING,
            ApiPaths.SYNC_WS,
            ApiPaths.DEVICES,
            ApiPaths.DEVICE_BY_ID,
            ApiPaths.FCM_REGISTER,
            ApiPaths.NOTIFICATIONS_UNREAD,
            ApiPaths.HEALTH_LIVE,
            ApiPaths.HEALTH_READY,
        ).map { it.removePrefix(ApiPaths.V1) }

        val missing = implemented
            .filterNot { it in paths || it in extensionPaths }
            .sorted()
        assertTrue(missing.isEmpty(), "не задокументированы и не в allowlist: $missing")
    }

    @Test
    fun `login contract does not expose account lockout`() {
        val login = paths["/auth/login"] as Map<*, *>
        val responses = ((login["post"] as Map<*, *>)["responses"] as Map<*, *>).keys.map { it.toString() }
        assertTrue("401" in responses, "ожидается 401 invalid_credentials")
        assertFalse("403" in responses, "SEC-10: 403 account_locked не должен быть в контракте")
    }

    @Test
    fun `documented paths have post or get operations`() {
        paths.forEach { (path, value) ->
            val operations = value as Map<*, *>
            assertNotNull(
                operations.keys.firstOrNull { it == "get" || it == "post" || it == "patch" || it == "delete" },
                "у пути $path нет операции",
            )
        }
    }
}
