package io.github.slavikjunior.syncclip.shared.crypto

import java.security.SecureRandom

private val random = SecureRandom()

public actual fun secureRandomBytes(size: Int): ByteArray {
    require(size >= 0) { "size must be non-negative" }
    val bytes = ByteArray(size)
    random.nextBytes(bytes)
    return bytes
}
