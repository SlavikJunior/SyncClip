package io.github.slavikjunior.syncclip.shared.crypto

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Сверка собственной AES-256-GCM с доверенной реализацией JDK.
 *
 * Общий код на Kotlin — единственный способ получить одинаковый шифротекст на
 * JVM, Android и iOS, но корректность руками не проверить. Здесь 50 случайных
 * пакетов шифруются обоими движками в обе стороны.
 */
internal class AesGcmCrossCheckTest {

    private val random = SecureRandom()

    @Test
    fun `kotlin engine matches javax crypto`() {
        repeat(50) { iteration ->
            val key = ByteArray(32).also(random::nextBytes)
            val iv = ByteArray(12).also(random::nextBytes)
            val size = random.nextInt(600)
            val plaintext = ByteArray(size).also(random::nextBytes)

            val ours = AesGcm.encrypt(plaintext, key, iv)
            val reference = javaxEncrypt(plaintext, key, iv)

            assertTrue(
                ours.contentEquals(reference),
                "iteration $iteration: шифротекст отличается от javax.crypto",
            )
            assertTrue(
                AesGcm.decrypt(reference, key).contentEquals(plaintext),
                "iteration $iteration: не расшифровали пакет javax.crypto",
            )
        }
    }

    @Test
    fun `text payload is readable by javax crypto`() {
        val cipher = AesGcmPayloadCipher()
        val key = ByteArray(32).also(random::nextBytes)
        val text = "Hello, SyncClip! Кириллица и 🚀"

        val payload = Base64.getDecoder().decode(cipher.encrypt(text, key))
        val iv = payload.copyOfRange(0, 12)
        val ciphertextAndTag = payload.copyOfRange(12, payload.size)

        val engineered = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            doFinal(ciphertextAndTag)
        }

        assertEquals(text, engineered.decodeToString())
    }

    private fun javaxEncrypt(plaintext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val encrypted = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            doFinal(plaintext)
        }
        return iv + encrypted
    }
}
