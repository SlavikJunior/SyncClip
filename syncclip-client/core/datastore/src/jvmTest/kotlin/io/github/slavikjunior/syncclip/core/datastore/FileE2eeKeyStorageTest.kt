package io.github.slavikjunior.syncclip.core.datastore

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Desktop-хранилище ключа: файл с правами владельца.
 *
 * Проверяются и байты, и права доступа — без `rw-------` ключ был бы доступен
 * другим пользователям машины.
 */
internal class FileE2eeKeyStorageTest {

    private val directory: Path = Files.createTempDirectory("syncclip-key")
    private val file: Path = directory.resolve("e2ee.key")
    private val storage = FileE2eeKeyStorage(file)

    @Test
    fun `key round trips through the file`() = runTest {
        val key = ByteArray(32) { (it * 5).toByte() }

        storage.save(key)

        assertContentEquals(key, storage.load())
        assertTrue(Files.exists(file))

        storage.clear()
        assertNull(storage.load())
        assertFalse(Files.exists(file))
    }

    @Test
    fun `file is readable by owner only`() = runTest {
        storage.save(ByteArray(32) { it.toByte() })

        val permissions = Files.getPosixFilePermissions(file)

        assertTrue(PosixFilePermission.OWNER_READ in permissions)
        assertTrue(PosixFilePermission.OWNER_WRITE in permissions)
        assertFalse(PosixFilePermission.GROUP_READ in permissions)
        assertFalse(PosixFilePermission.OTHERS_READ in permissions)
    }

    @Test
    fun `factory persists the key for the next process`() = runTest {
        val home = Files.createTempDirectory("syncclip-home")
        val previous = System.getProperty("user.home")
        System.setProperty("user.home", home.toString())
        try {
            val key = ByteArray(32) { (it + 11).toByte() }
            createE2eeKeyStore().setKey(key)

            // Второй экземпляр — как новый запуск приложения.
            val next = createE2eeKeyStore()
            next.hydrate()

            assertContentEquals(key, next.key())
        } finally {
            System.setProperty("user.home", previous)
        }
    }

    @Test
    fun `truncated file is treated as absent`() = runTest {
        Files.createDirectories(directory)
        Files.write(file, byteArrayOf(1, 2, 3))

        assertNull(storage.load())
    }
}
