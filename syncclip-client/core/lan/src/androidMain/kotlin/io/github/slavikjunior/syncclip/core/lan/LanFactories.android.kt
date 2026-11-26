package io.github.slavikjunior.syncclip.core.lan

import android.content.Context

public actual fun createLanDiscovery(
    advertisement: LanAdvertisement,
    platformContext: Any?,
): LanDiscovery {
    val context = platformContext as? Context
        ?: error("Android требует Context для NsdManager")
    return AndroidNsdLanDiscovery(advertisement, context)
}

public actual fun createLanDialer(platformContext: Any?): LanDialer = JavaTcpLanDialer()

public actual fun createLanServer(platformContext: Any?): LanServer = JavaTcpLanServer()
