package io.github.slavikjunior.syncclip.core.lan

import kotlinx.coroutines.flow.StateFlow

/** Обнаружение пиров в локальной сети. */
public interface LanDiscovery {

    /** Найденные пиры; обновляется по мере анонсов и таймаутов. */
    public val peers: StateFlow<List<LanPeer>>

    public suspend fun start()

    public suspend fun stop()
}
