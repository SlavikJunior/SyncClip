package io.github.slavikjunior.syncclip.shared.crypto

/**
 * Криптостойкий генератор случайных байт платформы.
 *
 * IV и canary обязаны быть непредсказуемыми: `kotlin.random.Random` здесь
 * непригоден, поэтому каждая платформа использует свой CSPRNG
 * (`SecureRandom`, `SecRandomCopyBytes`).
 */
public expect fun secureRandomBytes(size: Int): ByteArray
