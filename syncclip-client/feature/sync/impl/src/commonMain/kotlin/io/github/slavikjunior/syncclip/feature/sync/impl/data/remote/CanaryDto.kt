package io.github.slavikjunior.syncclip.feature.sync.impl.data.remote

import kotlinx.serialization.Serializable

/** `POST /v1/auth/canary` — тот же DTO, что и в ответе `GET`. */
@Serializable
internal data class CanaryDto(val encryptedCanary: String)

/** `POST /v1/auth/canary/attempt` — отчёт о попытке ввода (FR-1.4). */
@Serializable
internal data class CanaryAttemptRequest(val success: Boolean)

@Serializable
internal data class CanaryAttemptResponse(val remainingAttempts: Int)
