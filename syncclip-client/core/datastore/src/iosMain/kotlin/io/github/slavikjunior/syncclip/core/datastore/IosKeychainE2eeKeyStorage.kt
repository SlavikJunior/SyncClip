package io.github.slavikjunior.syncclip.core.datastore

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSCopyingProtocol
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

/**
 * Ключ E2EE в iOS Keychain.
 *
 * Запись `kSecClassGenericPassword` с доступностью
 * `AfterFirstUnlockThisDeviceOnly`: ключ читается после разблокировки устройства
 * и не попадает в iCloud/резервные копии на другое устройство.
 *
 * CF-константы Keychain — это `CFStringRef`, а не Kotlin-строки. Прямой
 * `CFBridgingRetain(mapOf(kSecClass to …))` даёт `errSecParam (-50)`, поэтому
 * словарь собирается через `NSMutableDictionary` с явным toll-free bridging
 * каждой константы.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosKeychainE2eeKeyStorage : E2eeKeyStorage {

    override suspend fun load(): ByteArray? {
        val query = baseQuery().apply {
            set(cfToObject(kSecReturnData), cfToObject(kCFBooleanTrue))
            set(cfToObject(kSecMatchLimit), cfToObject(kSecMatchLimitOne))
        }
        return withQueryDictionary(query) { queryRef ->
            memScoped {
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(queryRef, result.ptr)
                if (status != errSecSuccess) return@memScoped null
                (CFBridgingRelease(result.value) as? NSData)
                    ?.toByteArray()
                    ?.takeIf { it.size == KEY_BYTES }
            }
        }
    }

    override suspend fun save(key: ByteArray) {
        clear()
        val attributes = baseQuery().apply {
            set(cfToObject(kSecValueData), key.toNSData())
            set(
                cfToObject(kSecAttrAccessible),
                cfToObject(kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly),
            )
        }
        withQueryDictionary(attributes) { queryRef ->
            SecItemAdd(queryRef, null)
        }
    }

    override suspend fun clear() {
        withQueryDictionary(baseQuery()) { queryRef ->
            SecItemDelete(queryRef)
        }
    }

    private fun baseQuery(): NSMutableDictionary = NSMutableDictionary().apply {
        set(cfToObject(kSecClass), cfToObject(kSecClassGenericPassword))
        set(cfToObject(kSecAttrService), SERVICE)
        set(cfToObject(kSecAttrAccount), ACCOUNT)
    }

    /**
     * CF-константа → Objective-C объект для помещения в `NSMutableDictionary`.
     *
     * `CFRetain` + `CFBridgingRelease` — стандартная пара toll-free bridging:
     * словарь сам удержит объект, поэтому дополнительный retain обязателен.
     */
    private fun cfToObject(value: CFTypeRef?): Any? =
        value?.let { retained -> CFBridgingRelease(CFRetain(retained)) }

    private fun NSMutableDictionary.set(key: Any?, value: Any?) {
        if (key == null || value == null) return
        @Suppress("UNCHECKED_CAST")
        setObject(value, forKey = key as NSCopyingProtocol)
    }

    private inline fun <T> withQueryDictionary(
        dictionary: NSMutableDictionary,
        block: (CFDictionaryRef?) -> T,
    ): T {
        val queryRef = CFBridgingRetain(dictionary) as? CFDictionaryRef
        return try {
            block(queryRef)
        } finally {
            queryRef?.let { CFRelease(it) }
        }
    }

    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        val result = ByteArray(size)
        if (size == 0) return result
        val source = bytes ?: return result
        result.usePinned { pinned -> memcpy(pinned.addressOf(0), source, length) }
        return result
    }

    private companion object {
        const val SERVICE = "io.github.slavikjunior.syncclip.e2ee"
        const val ACCOUNT = "e2ee-key"
        const val KEY_BYTES = 32
    }
}

internal actual fun createE2eeKeyStorage(platformContext: Any?): E2eeKeyStorage =
    IosKeychainE2eeKeyStorage()
