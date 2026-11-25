package io.github.slavikjunior.syncclip.shared.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
public actual fun secureRandomBytes(size: Int): ByteArray {
    require(size >= 0) { "size must be non-negative" }
    val bytes = ByteArray(size)
    if (size == 0) return bytes
    bytes.usePinned { pinned ->
        val status = SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0))
        check(status == errSecSuccess) { "SecRandomCopyBytes failed: $status" }
    }
    return bytes
}
