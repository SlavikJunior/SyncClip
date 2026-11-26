package io.github.slavikjunior.syncclip.core.lan

/** Клиент LAN: открывает соединение с пиром с таймаутом на connect/ACK. */
public fun interface LanDialer {

    /** @return поток или `null`, если пир недоступен/таймаут. */
    public suspend fun dial(host: String, port: Int, timeoutMillis: Long): LanRawStream?
}
