package io.github.slavikjunior.syncclip.core.datastore

/**
 * Платформенное защищённое хранилище ключа E2EE.
 *
 * - Android: ключ шифруется ключом Android Keystore (AES/GCM), шифротекст
 *   лежит в приватном каталоге приложения;
 * - iOS: Keychain (`kSecClassGenericPassword`,
 *   `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`);
 * - JVM: файл `~/.syncclip/e2ee.key` с правами владельца (POSIX 600).
 *
 * Контракт suspend: Keystore/Keychain/диск — блокирующие операции, их нельзя
 * выполнять на UI-потоке.
 */
internal interface E2eeKeyStorage {

    suspend fun load(): ByteArray?

    suspend fun save(key: ByteArray)

    suspend fun clear()
}

/** Фабрика платформенного хранилища; [platformContext] нужен только Android. */
internal expect fun createE2eeKeyStorage(platformContext: Any? = null): E2eeKeyStorage
