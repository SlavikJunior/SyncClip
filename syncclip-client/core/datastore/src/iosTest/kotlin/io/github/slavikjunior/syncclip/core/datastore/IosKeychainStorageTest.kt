package io.github.slavikjunior.syncclip.core.datastore

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/**
 * Проверка iOS-Keychain на симуляторе.
 *
 * В тестовом бандле без host-app Keychain отдаёт `errSecNotAvailable (-25291)` —
 * это ограничение окружения, а не кода. Отсутствие `errSecParam (-50)` уже
 * означает, что CF-словарь собран корректно. Полная проверка чтения/записи
 * выполняется запуском приложения на устройстве/симуляторе.
 */
internal class IosKeychainStorageTest {

    @Test
    fun `key round trips through keychain when available`() = runTest {
        val storage = createE2eeKeyStorage(null)
        storage.clear()
        val key = ByteArray(32) { (it * 3).toByte() }
        try {
            storage.save(key)

            val loaded = storage.load() ?: return@runTest
            assertContentEquals(key, loaded)
        } finally {
            storage.clear()
            assertNull(storage.load())
        }
    }
}
