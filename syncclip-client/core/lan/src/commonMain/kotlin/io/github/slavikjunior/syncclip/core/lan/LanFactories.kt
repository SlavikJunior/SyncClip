package io.github.slavikjunior.syncclip.core.lan

/** Платформенные фабрики LAN-слоя; [platformContext] нужен только Android. */
public expect fun createLanDiscovery(
    advertisement: LanAdvertisement,
    platformContext: Any? = null,
): LanDiscovery

public expect fun createLanDialer(platformContext: Any? = null): LanDialer

public expect fun createLanServer(platformContext: Any? = null): LanServer
