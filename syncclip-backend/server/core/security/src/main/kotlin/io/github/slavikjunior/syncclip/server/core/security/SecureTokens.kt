package io.github.slavikjunior.syncclip.server.core.security

import java.security.SecureRandom
import java.util.Base64

/** Генерация непредсказуемых значений: jti, invite-код (32 байта энтропии). */
class SecureTokens(private val random: SecureRandom = SecureRandom()) {

    fun newJti(): String = randomBytes(16)

    fun newInviteCode(): String = randomBytes(32)

    fun newConnectionId(): String = randomBytes(16)

    private fun randomBytes(size: Int): String {
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
