package io.github.slavikjunior.syncclip.server.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-HMAC-SHA256. Формат строки: `pbkdf2-sha256$<iterations>$<saltB64>$<hashB64>`.
 * Сравнение — constant-time; параметры хранятся в строке, поэтому рост итераций
 * не ломает проверку старых паролей.
 */
class Pbkdf2PasswordHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val saltLength: Int = 16,
    private val keyLength: Int = 32,
) : PasswordHasher {

    override val algorithm: String = ALGORITHM

    private val random = SecureRandom()
    private val encoder: Base64.Encoder = Base64.getEncoder()
    private val decoder: Base64.Decoder = Base64.getDecoder()

    override fun hash(password: String): String {
        require(password.isNotEmpty()) { "password must not be empty" }
        val salt = ByteArray(saltLength).also(random::nextBytes)
        val derived = derive(password, salt, iterations, keyLength)
        return buildString {
            append(ALGORITHM).append('$')
            append(iterations).append('$')
            append(encoder.encodeToString(salt)).append('$')
            append(encoder.encodeToString(derived))
        }
    }

    override fun verify(password: String, encodedHash: String): Boolean {
        val parts = encodedHash.split('$')
        if (parts.size != 4 || parts[0] != ALGORITHM) return false
        val parsedIterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(parts[3]) }.getOrNull() ?: return false
        if (parsedIterations !in 1..10_000_000 || salt.isEmpty() || expected.isEmpty()) return false
        val actual = derive(password, salt, parsedIterations, expected.size * 8)
        return MessageDigest.isEqual(expected, actual)
    }

    override fun needsRehash(encodedHash: String): Boolean {
        val parts = encodedHash.split('$')
        if (parts.size != 4 || parts[0] != ALGORITHM) return true
        val parsedIterations = parts[1].toIntOrNull() ?: return true
        return parsedIterations < iterations
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int, bits: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, bits)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val ALGORITHM: String = "pbkdf2-sha256"
        const val DEFAULT_ITERATIONS: Int = 600_000
    }
}
