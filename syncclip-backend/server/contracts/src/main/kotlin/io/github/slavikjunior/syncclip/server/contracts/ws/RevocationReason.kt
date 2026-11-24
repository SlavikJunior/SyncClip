package io.github.slavikjunior.syncclip.server.contracts.ws

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Причины `SESSION_REVOKED` (WEBSOCKET_PROTOCOL §3.6). */
@Serializable
enum class RevocationReason {
    @SerialName("guest_session_expired")
    GUEST_SESSION_EXPIRED,

    @SerialName("device_removed")
    DEVICE_REMOVED,

    @SerialName("account_deleted")
    ACCOUNT_DELETED,

    @SerialName("token_invalidated")
    TOKEN_INVALIDATED,
}
