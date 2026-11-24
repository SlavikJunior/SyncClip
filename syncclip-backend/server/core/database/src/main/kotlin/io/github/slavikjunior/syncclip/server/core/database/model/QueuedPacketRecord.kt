package io.github.slavikjunior.syncclip.server.core.database.model

import java.time.Instant

data class QueuedPacketRecord(
    val id: String,
    val seq: Long,
    val userId: String,
    val targetDeviceId: String,
    val eventId: String,
    val originDeviceId: String,
    val encryptedPayload: ByteArray,
    val payloadHash: String,
    val clientTimestamp: Long,
    val createdAt: Instant,
    val expiresAt: Instant,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QueuedPacketRecord) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
