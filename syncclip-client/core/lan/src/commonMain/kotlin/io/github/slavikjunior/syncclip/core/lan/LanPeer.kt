package io.github.slavikjunior.syncclip.core.lan

/** Пиp в той же локальной сети, найденный через mDNS/NSD. */
public data class LanPeer(
    val deviceId: String,
    val userId: String,
    val host: String,
    val port: Int,
)
