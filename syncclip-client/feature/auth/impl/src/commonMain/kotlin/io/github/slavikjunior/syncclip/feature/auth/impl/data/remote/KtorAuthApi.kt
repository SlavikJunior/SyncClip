package io.github.slavikjunior.syncclip.feature.auth.impl.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/**
 * Ktor-реализация порта.
 *
 * Тело читается строкой и разбирается явно: один и тот же ответ может быть
 * либо DTO, либо `Error`, а `HttpClient` в проекте не настроен на
 * `expectSuccess`.
 */
internal class KtorAuthApi(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) : AuthApi {

    override suspend fun login(request: FirebaseLoginRequestDto): Result<AuthTokensDto> =
        runCatching {
            val response = client.post("v1/auth/firebase") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw AuthHttpException(response.status.value, body.errorCode())
            }
            json.decodeFromString<AuthTokensDto>(body)
        }

    override suspend fun loginPassword(request: PasswordAuthRequestDto): Result<AuthTokensDto> =
        passwordAuth("v1/auth/login", request)

    override suspend fun registerPassword(request: PasswordAuthRequestDto): Result<AuthTokensDto> =
        passwordAuth("v1/auth/register", request)

    private suspend fun passwordAuth(
        path: String,
        request: PasswordAuthRequestDto,
    ): Result<AuthTokensDto> = runCatching {
        val response = client.post(path) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw AuthHttpException(response.status.value, body.errorCode())
        }
        json.decodeFromString<AuthTokensDto>(body)
    }

    override suspend fun registerFcmToken(request: FcmRegisterRequestDto): Result<Unit> =
        runCatching {
            val response = client.post("v1/fcm/register") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (!response.status.isSuccess()) {
                throw AuthHttpException(response.status.value, response.bodyAsText().errorCode())
            }
        }

    private fun String.errorCode(): String? =
        runCatching { json.decodeFromString<ApiErrorDto>(this).error }.getOrNull()
}
