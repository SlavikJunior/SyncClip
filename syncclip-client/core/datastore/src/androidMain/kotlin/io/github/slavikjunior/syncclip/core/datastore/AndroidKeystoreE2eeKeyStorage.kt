package io.github.slavikjunior.syncclip.core.datastore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ключ E2EE, зашифрованный ключом Android Keystore.
 *
 * Ключ Keystore (`syncclip.e2ee.wrap`) не покидает TEE/StrongBox и не может быть
 * извлечён; в приватном каталоге приложения лежит только шифротекст с IV.
 * Прямое хранение ключа в Keystore невозможно: Keystore не отдаёт SecretKey за
 * пределы процесса, поэтому используется envelope-схема.
 */
internal class AndroidKeystoreE2eeKeyStorage(
    private val context: Context,
) : E2eeKeyStorage {

    private val file = File(context.filesDir, KEY_FILE_NAME)

    override suspend fun load(): ByteArray? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        runCatching {
            val blob = file.readBytes()
            if (blob.size <= IV_BYTES) return@runCatching null
            decrypt(blob)
        }.getOrNull()?.takeIf { it.size == KEY_BYTES }
    }

    override suspend fun save(key: ByteArray) {
        withContext(Dispatchers.IO) {
            val encrypted = encrypt(key)
            val temporary = File(file.parentFile, "$KEY_FILE_NAME.tmp")
            try {
                temporary.writeBytes(encrypted)
                if (!temporary.renameTo(file)) {
                    // File.renameTo не перезаписывает существующий файл.
                    file.delete()
                    check(temporary.renameTo(file)) { "cannot replace E2EE key file" }
                }
            } finally {
                temporary.delete()
            }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { file.delete() }
    }

    private fun encrypt(key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val ciphertext = cipher.doFinal(key)
        return cipher.iv + ciphertext
    }

    private fun decrypt(blob: ByteArray): ByteArray {
        val iv = blob.copyOfRange(0, IV_BYTES)
        val ciphertext = blob.copyOfRange(IV_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "syncclip.e2ee.wrap"
        const val KEY_FILE_NAME = "e2ee.key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_BYTES = 32
    }
}

internal actual fun createE2eeKeyStorage(platformContext: Any?): E2eeKeyStorage {
    val context = platformContext as? Context
        ?: error("Android требует Context для хранения ключа E2EE")
    return AndroidKeystoreE2eeKeyStorage(context.applicationContext)
}
