package io.github.slavikjunior.syncclip.core.lan

/** Создаёт дискавери для конкретного анонса (порт сервера известен после старта). */
public fun interface LanDiscoveryFactory {

    public fun create(advertisement: LanAdvertisement): LanDiscovery
}
