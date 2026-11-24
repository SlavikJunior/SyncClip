package io.github.slavikjunior.syncclip.server.core.security

/**
 * Хеширование пароля аккаунта.
 *
 * Отклонение от §7.2 дизайна: `argon2-jvm` не добавлен — библиотека помечена
 * «требует апрува», использует JNA и нативную сборку, что не гарантировано на
 * darwin-arm64. Реализация по умолчанию — PBKDF2WithHmacSHA256 (JDK, без
 * нативных зависимостей); интерфейс позволяет заменить её на Argon2id одной
 * строкой в DI, не меняя вызывающий код.
 */
interface PasswordHasher {
    /** Имя алгоритма для колонки `users.password_algo`. */
    val algorithm: String

    fun hash(password: String): String

    fun verify(password: String, encodedHash: String): Boolean

    /** true — хеш устарел (другой алгоритм или меньше итераций), нужен rehash-on-login. */
    fun needsRehash(encodedHash: String): Boolean
}
