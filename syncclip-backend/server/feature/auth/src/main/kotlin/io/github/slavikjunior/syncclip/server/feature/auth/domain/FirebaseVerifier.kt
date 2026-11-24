@file:Suppress("MagicNumber", "ThrowsCount", "TooGenericExceptionCaught")

package io.github.slavikjunior.syncclip.server.feature.auth.domain

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.exceptions.TokenExpiredException
import io.github.slavikjunior.syncclip.server.contracts.error.ErrorCode
import io.github.slavikjunior.syncclip.server.contracts.error.UnauthorizedException
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.time.Instant

/** Verified identity из Firebase ID token (SEC-10). */
data class FirebaseIdentity(
    val uid: String,
    val email: String,
    val emailVerified: Boolean,
)

interface FirebaseVerifier {
    fun verify(idToken: String): FirebaseIdentity
}

/**
 * Проверка Firebase ID token по публичным x509-сертификатам Google (JWKS).
 *
 * Service account не нужен: подпись RS256 проверяется публичным ключом из
 * `FIREBASE_JWKS_URL`. Кэш сертификатов обновляется по TTL из `Cache-Control`
 * и при неизвестном `kid`.
 */
class JwksFirebaseVerifier(
    private val projectId: String,
    private val jwksUrl: String,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build(),
    private val clock: () -> Instant = Instant::now,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : FirebaseVerifier {

    private data class CachedKeys(val keys: Map<String, PublicKey>, val expiresAt: Instant)

    @Volatile
    private var cached: CachedKeys? = null

    override fun verify(idToken: String): FirebaseIdentity {
        val decoded = try {
            JWT.decode(idToken)
        } catch (e: Exception) {
            throw UnauthorizedException(ErrorCode.FIREBASE_TOKEN_INVALID, cause = e)
        }
        if (decoded.algorithm != "RS256") {
            throw UnauthorizedException(ErrorCode.FIREBASE_TOKEN_INVALID)
        }
        val kid = decoded.keyId ?: throw UnauthorizedException(ErrorCode.FIREBASE_TOKEN_INVALID)
        val publicKey = publicKey(kid)
            ?: throw UnauthorizedException(ErrorCode.FIREBASE_TOKEN_INVALID)

        val verifier = JWT.require(Algorithm.RSA256(publicKey as RSAPublicKey, null))
            .withIssuer("https://securetoken.google.com/$projectId")
            .withAudience(projectId)
            .withClaimPresence("exp")
            .build()
        val jwt = try {
            verifier.verify(idToken)
        } catch (e: TokenExpiredException) {
            throw UnauthorizedException(ErrorCode.TOKEN_EXPIRED, cause = e)
        } catch (e: JWTVerificationException) {
            throw UnauthorizedException(ErrorCode.FIREBASE_TOKEN_INVALID, cause = e)
        }
        val uid = jwt.subject ?: throw UnauthorizedException(ErrorCode.FIREBASE_TOKEN_INVALID)
        val email = jwt.getClaim("email").asString()
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
            ?: throw UnauthorizedException(ErrorCode.FIREBASE_TOKEN_INVALID)
        val emailVerified = jwt.getClaim("email_verified").asBoolean() ?: false
        return FirebaseIdentity(uid = uid, email = email, emailVerified = emailVerified)
    }

    private fun publicKey(kid: String): PublicKey? {
        val now = clock()
        val current = cached
        if (current != null && current.expiresAt.isAfter(now) && kid in current.keys) {
            return current.keys[kid]
        }
        return synchronized(this) {
            val fresh = cached
            if (fresh != null && fresh.expiresAt.isAfter(now) && kid in fresh.keys) {
                fresh.keys[kid]
            } else {
                val loaded = fetchKeys()
                cached = loaded
                loaded.keys[kid]
            }
        }
    }

    private fun fetchKeys(): CachedKeys {
        val request = HttpRequest.newBuilder(URI.create(jwksUrl))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            throw UnauthorizedException(ErrorCode.FIREBASE_TOKEN_INVALID, cause = e)
        }
        if (response.statusCode() != 200) {
            throw UnauthorizedException(ErrorCode.FIREBASE_TOKEN_INVALID)
        }
        val body = response.body()
        val certs = try {
            json.decodeFromString<Map<String, String>>(body)
        } catch (e: Exception) {
            throw UnauthorizedException(ErrorCode.FIREBASE_TOKEN_INVALID, cause = e)
        }
        val factory = CertificateFactory.getInstance("X.509")
        val keys = certs.mapNotNull { (kid, pem) ->
            runCatching {
                val certificate = pem.byteInputStream(Charsets.UTF_8).use { factory.generateCertificate(it) }
                val key = certificate.publicKey
                if (key is RSAPublicKey) kid to key else null
            }.getOrNull()
        }.toMap()
        if (keys.isEmpty()) throw UnauthorizedException(ErrorCode.FIREBASE_TOKEN_INVALID)
        val ttl = response.headers().firstValue("Cache-Control").orElse(null)
            ?.let(::parseMaxAge)
            ?: DEFAULT_TTL
        return CachedKeys(keys = keys, expiresAt = clock().plusSeconds(ttl))
    }

    private fun parseMaxAge(header: String): Long? =
        header.split(',')
            .map { it.trim() }
            .firstOrNull { it.startsWith("max-age=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.toLongOrNull()
            ?.takeIf { it > 0 }

    private companion object {
        const val DEFAULT_TTL = 3_600L
    }
}
