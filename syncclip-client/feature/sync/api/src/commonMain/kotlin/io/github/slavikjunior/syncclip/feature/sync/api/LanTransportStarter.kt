package io.github.slavikjunior.syncclip.feature.sync.api

/**
 * Публичный вход в LAN-транспорт (INV-27).
 *
 * Запускается composition root на время сессии: поднимает TCP-сервер, NSD-анонс
 * и параллельную отправку пирам с fallback на сервер.
 */
public interface LanTransportStarter {

    public suspend fun run()
}
