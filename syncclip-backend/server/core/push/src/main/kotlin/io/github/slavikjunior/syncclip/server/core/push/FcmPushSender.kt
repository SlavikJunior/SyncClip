package io.github.slavikjunior.syncclip.server.core.push

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * FCM HTTP v1 без Firebase Admin SDK.
 *
 * Причина отклонения от §11.4: firebase-admin тянет grpc/netty и конфликтует с
 * Netty Ktor (сам дизайн предупреждает об этом в Приложении A). Здесь —
 * OAuth2 service-account (RS256, JDK crypto) + `messages:send` через
 * `java.net.http`. Ленивая инициализация: сбой credentials не валит старт.
 */
class FcmPushSender(
    private val credentialsPath: String,
    private val projectIdOverride: String? = null,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
) : PushSender {

    private val logger = LoggerFactory.getLogger(FcmPushSender::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var serviceAccount: ServiceAccount? = null

    @Volatile
    private var cachedToken: AccessToken? = null

    override suspend fun send(tokens: List<String>, message: PushMessage): PushResult =
        withContext(Dispatchers.IO) {
            if (tokens.isEmpty()) return@withContext PushResult(delivered = 0)
            val account = serviceAccount ?: loadServiceAccount()?.also { serviceAccount = it }
                ?: return@withContext PushResult(delivered = 0, retryable = true)

            var delivered = 0
            val invalid = mutableListOf<String>()
            var retryable = false
            for (token in tokens.take(PushSender.MAX_TOKENS_PER_CALL)) {
                when (val outcome = sendOne(account, token, message)) {
                    SendOutcome.Delivered -> delivered++
                    SendOutcome.Invalid -> invalid += token
                    SendOutcome.Retryable -> retryable = true
                }
            }
            if (invalid.isNotEmpty()) {
                logger.warn("fcm invalid tokens: {}", invalid.size)
            }
            PushResult(delivered = delivered, invalidTokens = invalid, retryable = retryable)
        }

    private fun sendOne(account: ServiceAccount, token: String, message: PushMessage): SendOutcome {
        val accessToken = accessToken(account) ?: return SendOutcome.Retryable
        val payload = buildJsonObject {
            putJsonObject("message") {
                put("token", token)
                putJsonObject("notification") {
                    put("title", message.title)
                    put("body", message.body)
                }
                if (message.data.isNotEmpty()) {
                    putJsonObject("data") {
                        message.data.forEach { (key, value) -> put(key, value) }
                    }
                }
                if (message.collapseKey != null) {
                    putJsonObject("android") { put("collapse_key", message.collapseKey) }
                    putJsonObject("webpush") {
                        putJsonObject("headers") { put("Urgency", "normal") }
                    }
                }
            }
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://fcm.googleapis.com/v1/projects/${account.projectId}/messages:send"))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build()
        val response = runCatching { httpClient.send(request, HttpResponse.BodyHandlers.ofString()) }
            .getOrElse {
                logger.warn("fcm send failed: {}", it.javaClass.simpleName)
                return SendOutcome.Retryable
            }
        return when (response.statusCode()) {
            200 -> SendOutcome.Delivered
            400, 404 -> if (isInvalidToken(response.body())) SendOutcome.Invalid else SendOutcome.Retryable
            401, 403 -> {
                cachedToken = null
                SendOutcome.Retryable
            }
            in 500..599 -> SendOutcome.Retryable
            else -> SendOutcome.Retryable
        }
    }

    private fun isInvalidToken(body: String): Boolean =
        body.contains("UNREGISTERED") || body.contains("INVALID_ARGUMENT") ||
            body.contains("SENDER_ID_MISMATCH") || body.contains("NOT_FOUND")

    private fun accessToken(account: ServiceAccount): String? {
        val current = cachedToken
        if (current != null && current.expiresAt.isAfter(Instant.now().plusSeconds(60))) {
            return current.value
        }
        val assertion = signAssertion(account) ?: return null
        val form = "grant_type=" + URLEncoder.encode(GRANT_TYPE, Charsets.UTF_8) +
            "&assertion=" + URLEncoder.encode(assertion, Charsets.UTF_8)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(account.tokenUri))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val response = runCatching { httpClient.send(request, HttpResponse.BodyHandlers.ofString()) }
            .getOrElse {
                logger.warn("fcm oauth failed: {}", it.javaClass.simpleName)
                return null
            }
        if (response.statusCode() != 200) {
            logger.warn("fcm oauth status={}", response.statusCode())
            return null
        }
        val parsed = runCatching {
            json.parseToJsonElement(response.body()).jsonObject
        }.getOrNull() ?: return null
        val value = parsed["access_token"]?.jsonPrimitive?.content ?: return null
        val ttl = parsed["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600
        val token = AccessToken(value, Instant.now().plusSeconds(ttl))
        cachedToken = token
        return token.value
    }

    private fun signAssertion(account: ServiceAccount): String? = runCatching {
        val now = Instant.now()
        val header = base64Url("""{"alg":"RS256","typ":"JWT"}""")
        val claims = base64Url(
            buildJsonObject {
                put("iss", account.clientEmail)
                put("scope", FCM_SCOPE)
                put("aud", account.tokenUri)
                put("iat", now.epochSecond)
                put("exp", now.epochSecond + 3600)
            }.toString(),
        )
        val signingInput = "$header.$claims"
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(account.privateKey)
            update(signingInput.toByteArray(Charsets.US_ASCII))
            sign()
        }
        "$signingInput.${Base64.getUrlEncoder().withoutPadding().encodeToString(signature)}"
    }.onFailure { logger.warn("fcm assertion signing failed: {}", it.javaClass.simpleName) }
        .getOrNull()

    private fun loadServiceAccount(): ServiceAccount? = runCatching {
        val raw = Files.readString(Path.of(credentialsPath))
        val obj: JsonObject = json.parseToJsonElement(raw).jsonObject
        val clientEmail = obj["client_email"]?.jsonPrimitive?.content
            ?: error("client_email is missing")
        val privateKeyPem = obj["private_key"]?.jsonPrimitive?.content
            ?: error("private_key is missing")
        val tokenUri = obj["token_uri"]?.jsonPrimitive?.content
            ?: "https://oauth2.googleapis.com/token"
        val projectId = projectIdOverride ?: obj["project_id"]?.jsonPrimitive?.content
            ?: error("project_id is missing")
        val keyBytes = Base64.getDecoder().decode(
            privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), ""),
        )
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        ServiceAccount(projectId, clientEmail, privateKey, tokenUri)
    }.onFailure { logger.warn("fcm credentials unavailable: {}", it.javaClass.simpleName) }
        .getOrNull()

    private fun base64Url(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private data class ServiceAccount(
        val projectId: String,
        val clientEmail: String,
        val privateKey: java.security.PrivateKey,
        val tokenUri: String,
    )

    private data class AccessToken(val value: String, val expiresAt: Instant)

    private enum class SendOutcome { Delivered, Invalid, Retryable }

    private companion object {
        const val FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
        const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
    }
}
