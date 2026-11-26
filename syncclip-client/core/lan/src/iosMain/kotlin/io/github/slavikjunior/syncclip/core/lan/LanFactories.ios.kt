package io.github.slavikjunior.syncclip.core.lan

public actual fun createLanDiscovery(
    advertisement: LanAdvertisement,
    platformContext: Any?,
): LanDiscovery = IosNsdLanDiscovery(advertisement)

public actual fun createLanDialer(platformContext: Any?): LanDialer = IosPosixLanDialer()

public actual fun createLanServer(platformContext: Any?): LanServer = IosPosixLanServer()
