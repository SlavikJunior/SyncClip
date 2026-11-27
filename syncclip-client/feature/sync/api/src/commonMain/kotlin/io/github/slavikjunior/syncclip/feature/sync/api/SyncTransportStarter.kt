package io.github.slavikjunior.syncclip.feature.sync.api

/**
 * Публичный вход в WebSocket-транспорт (INV-27).
 *
 * Запускается composition root на время сессии. Транспорт сам ждёт, пока
 * введён мастер-пароль: без ключа E2EE отправлять нечего.
 */
public interface SyncTransportStarter {

    /** Работает до отмены корутины; переподключается с экспоненциальным backoff. */
    public suspend fun run()
}
