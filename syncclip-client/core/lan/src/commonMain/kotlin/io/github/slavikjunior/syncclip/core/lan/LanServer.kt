package io.github.slavikjunior.syncclip.core.lan

/**
 * Приём LAN-соединений.
 *
 * Каждое соединение обслуживается в своей корутине: один пир не должен
 * задерживать ACK другому (FR-2.6 — параллельная отправка).
 */
public interface LanServer {

    /**
     * @param handle получает поток соединения; должен прочитать кадр, записать
     *   ACK и закрыть поток.
     * @return фактический TCP-порт — его публикует mDNS.
     */
    public suspend fun start(handle: suspend (LanRawStream) -> Unit): Int

    public suspend fun stop()
}
