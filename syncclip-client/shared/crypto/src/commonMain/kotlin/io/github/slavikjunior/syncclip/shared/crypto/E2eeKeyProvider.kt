package io.github.slavikjunior.syncclip.shared.crypto

/**
 * Ключ E2EE на текущую сессию.
 *
 * Ключ не сериализуется и не попадает в БД: он живёт в памяти, пока приложение
 * открыто, и заново выводится из мастер-пароля при следующем запуске.
 */
public interface E2eeKeyProvider {

    /** `null` — мастер-пароль ещё не введён, шифровать нечем. */
    public suspend fun key(): ByteArray?
}
