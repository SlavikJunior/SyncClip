package io.github.slavikjunior.syncclip.server.core.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Псевдонимизация email для журнала `auth_attempts` (SEC-12).
 *
 * Голый SHA-256 позволяет перебрать адреса по дампу БД; HMAC с серверным
 * pepper'ом (выводится из `JWT_SECRET` с доменным разделением) делает такой
 * перебор бессмысленным без доступа к секрету приложения.
 */
class AuditHasher(secret: String) {

    private val key = SecretKeySpec(
        "syncclip-auth-audit".toByteArray(Charsets.UTF_8) + secret.toByteArray(Charsets.UTF_8),
        "HmacSHA256",
    )

    fun emailHash(email: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        val digest = mac.doFinal(email.trim().lowercase().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
