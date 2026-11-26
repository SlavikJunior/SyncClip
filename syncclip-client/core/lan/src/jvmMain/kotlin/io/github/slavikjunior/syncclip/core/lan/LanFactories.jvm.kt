package io.github.slavikjunior.syncclip.core.lan

public actual fun createLanDiscovery(
    advertisement: LanAdvertisement,
    platformContext: Any?,
): LanDiscovery = JmDnsLanDiscovery(advertisement)

public actual fun createLanDialer(platformContext: Any?): LanDialer = JavaTcpLanDialer()

public actual fun createLanServer(platformContext: Any?): LanServer = JavaTcpLanServer()
