package io.github.slavikjunior.syncclip.feature.sync.impl.domain.model

internal data class SyncPacket(
    val text: String,
    val copiedAtMillis: Long,
    val isProgrammatic: Boolean = false,
)
