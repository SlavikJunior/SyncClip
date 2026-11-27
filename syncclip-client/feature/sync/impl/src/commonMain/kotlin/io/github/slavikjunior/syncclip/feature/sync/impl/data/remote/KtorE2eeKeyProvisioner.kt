package io.github.slavikjunior.syncclip.feature.sync.impl.data.remote

import io.github.slavikjunior.syncclip.core.datastore.E2eeLockoutStore
import io.github.slavikjunior.syncclip.core.datastore.SessionState
import io.github.slavikjunior.syncclip.core.datastore.SessionStore
import io.github.slavikjunior.syncclip.feature.sync.api.E2eeKeyProvisioner
import io.github.slavikjunior.syncclip.feature.sync.api.E2eeUnlockResult
import io.github.slavikjunior.syncclip.shared.crypto.CryptoException
import io.github.slavikjunior.syncclip.shared.crypto.PayloadCipher
import io.github.slavikjunior.syncclip.shared.crypto.Pbkdf2
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * Выводит ключ E2EE из мастер-пароля и сверяет его с canary сервера.
 *
 * Сценарии (FR-1.3):
 * - canary есть → расшифровываем его введённым паролем и проверяем префикс;
 * - canary нет (404) → это первое устройство: создаём canary и ключ;
 * - ответ 409 (успели с другого устройства) → перечитываем и проверяем.
 *
 * Лимиты попыток (FR-1.4): сервер считает их сам (`canary/attempt`), клиент
 * запоминает `remainingAttempts`/`Retry-After` в [E2eeLockoutStore] и не
 * отправляет запрос, пока блокировка активна.
 */
internal class KtorE2eeKeyProvisioner(
    private val client: HttpClient,
    private val session: SessionStore,
    private val cipher: PayloadCipher,
    private val lockout: E2eeLockoutStore? = null,
    private val iterations: Int = Pbkdf2.DEFAULT_ITERATIONS,
    private val clock: () -> Long = {
        kotlin.time.Clock.System.now().toEpochMilliseconds()
    },
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) : E2eeKeyProvisioner {

    override suspend fun unlock(masterPassword: String): E2eeUnlockResult {
        val userId = (session.state.value as? SessionState.SignedIn)?.session?.userId
            ?: return E2eeUnlockResult.Unavailable("session is not signed in")

        val now = clock()
        lockout?.current()?.let { state ->
            if (state.isLocked(now)) {
                // Запрос не отправляем: сервер всё равно ответит 403.
                return E2eeUnlockResult.Locked(state.remainingSeconds(now))
            }
        }

        val key = Pbkdf2.deriveKey(masterPassword, salt = userId, iterations = iterations)
        return try {
            whenCanary(key, allowEnroll = true, nowMillis = now)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            E2eeUnlockResult.Unavailable(error.message ?: "network error")
        }
    }

    private suspend fun whenCanary(
        key: ByteArray,
        allowEnroll: Boolean,
        nowMillis: Long,
    ): E2eeUnlockResult {
        val response = client.get("$CANARY_PATH")
        val body = response.bodyAsText()
        if (response.status.value == 404 && allowEnroll) {
            return enroll(key)
        }
        if (!response.status.isSuccess()) {
            return E2eeUnlockResult.Unavailable("HTTP ${response.status.value}")
        }

        val encrypted = runCatching {
            json.decodeFromString<CanaryDto>(body).encryptedCanary
        }.getOrNull() ?: return E2eeUnlockResult.Unavailable("malformed canary")

        val canary = try {
            cipher.decrypt(encrypted, key)
        } catch (_: CryptoException) {
            null
        }
        return if (canary?.startsWith(CANARY_PREFIX) == true) {
            reportAttempt(success = true)
            lockout?.clear()
            E2eeUnlockResult.Success(key)
        } else {
            onFailedAttempt(nowMillis)
        }
    }

    private suspend fun enroll(key: ByteArray): E2eeUnlockResult {
        val canary = "$CANARY_PREFIX${Uuid.random()}"
        val response = client.post("$CANARY_PATH") {
            contentType(ContentType.Application.Json)
            setBody(CanaryDto(encryptedCanary = cipher.encrypt(canary, key)))
        }
        return when {
            response.status.isSuccess() -> {
                lockout?.clear()
                E2eeUnlockResult.Success(key)
            }
            // Другое устройство успело создать canary между GET и POST.
            response.status.value == 409 -> whenCanary(key, allowEnroll = false, nowMillis = clock())
            else -> E2eeUnlockResult.Unavailable("HTTP ${response.status.value}")
        }
    }

    private suspend fun onFailedAttempt(nowMillis: Long): E2eeUnlockResult =
        when (val report = reportAttempt(success = false)) {
            is AttemptReport.Counted -> {
                lockout?.recordAttempt(report.remaining)
                E2eeUnlockResult.InvalidPassword(report.remaining)
            }

            is AttemptReport.Locked -> {
                lockout?.lockUntil(nowMillis + report.retryAfterSeconds * 1_000)
                E2eeUnlockResult.Locked(report.retryAfterSeconds)
            }

            AttemptReport.Reset -> E2eeUnlockResult.InvalidPassword(null)
            AttemptReport.Unavailable -> E2eeUnlockResult.InvalidPassword(null)
        }

    private suspend fun reportAttempt(success: Boolean): AttemptReport {
        // Ошибка отчёта не должна блокировать разблокировку: сервер лишь
        // применяет лимиты и рассылает push.
        val response = runCatching {
            client.post("$CANARY_PATH/attempt") {
                contentType(ContentType.Application.Json)
                setBody(CanaryAttemptRequest(success))
            }
        }.getOrNull() ?: return AttemptReport.Unavailable

        val body = response.bodyAsText()
        if (response.status.isSuccess()) {
            val remaining = runCatching {
                json.decodeFromString<CanaryAttemptResponse>(body).remainingAttempts
            }.getOrNull() ?: 0
            return if (success) AttemptReport.Reset else AttemptReport.Counted(remaining)
        }

        if (response.status.value == 403) {
            val retryAfter = response.retryAfterSeconds() ?: body.retryAfterSeconds(json)
            if (retryAfter != null) return AttemptReport.Locked(retryAfter)
        }
        return AttemptReport.Unavailable
    }

    private fun HttpResponse.retryAfterSeconds(): Long? =
        headers[HttpHeaders.RetryAfter]
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { it > 0 }

    private fun String.retryAfterSeconds(parser: Json): Long? = runCatching {
        parser.decodeFromString<CanaryLockedDto>(this).retryAfter
    }.getOrNull()?.takeIf { it > 0 }

    private sealed interface AttemptReport {
        data object Reset : AttemptReport
        data class Counted(val remaining: Int) : AttemptReport
        data class Locked(val retryAfterSeconds: Long) : AttemptReport
        data object Unavailable : AttemptReport
    }

    private companion object {
        const val CANARY_PATH = "v1/auth/canary"
        const val CANARY_PREFIX = "syncclip-canary-"
    }
}
