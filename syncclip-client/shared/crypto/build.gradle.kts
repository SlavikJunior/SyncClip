plugins {
    id("convention.kmp.library")
}

/**
 * E2EE-криптография клиента: SHA-256, HMAC, PBKDF2 и AES-256-GCM.
 *
 * Реализация на чистом Kotlin в `commonMain`: CryptoKit недоступен из
 * Kotlin/Native, а `javax.crypto` — только JVM/Android. Один код на всех
 * платформах даёт одинаковый шифротекст; на JVM он дополнительно сверяется
 * с `javax.crypto` в тестах (`src/jvmTest`).
 */
