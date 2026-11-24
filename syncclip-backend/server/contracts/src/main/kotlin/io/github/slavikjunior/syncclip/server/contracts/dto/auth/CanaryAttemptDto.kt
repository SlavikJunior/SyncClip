package io.github.slavikjunior.syncclip.server.contracts.dto.auth

import kotlinx.serialization.Serializable

/** CG-01: отчёт клиента о попытке ввода мастер-пароля. */
@Serializable
data class CanaryAttemptRequest(val success: Boolean)

@Serializable
data class CanaryAttemptResponse(val remainingAttempts: Int)
