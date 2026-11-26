package io.github.slavikjunior.syncclip.core.di

import io.github.slavikjunior.syncclip.core.lan.LanDialer
import io.github.slavikjunior.syncclip.core.lan.LanDiscoveryFactory
import io.github.slavikjunior.syncclip.core.lan.LanServer
import io.github.slavikjunior.syncclip.core.lan.createLanDialer
import io.github.slavikjunior.syncclip.core.lan.createLanDiscovery
import io.github.slavikjunior.syncclip.core.lan.createLanServer
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * LAN-слой: TCP-сервер, клиент и NSD-дискавери.
 *
 * [platformContext] нужен Android для `NsdManager`; на iOS/Desktop — `null`.
 */
public fun lanModule(platformContext: Any? = null): Module = module {
    single<LanServer> { createLanServer(platformContext) }
    single<LanDialer> { createLanDialer(platformContext) }
    single<LanDiscoveryFactory> {
        LanDiscoveryFactory { advertisement ->
            createLanDiscovery(advertisement, platformContext)
        }
    }
}
