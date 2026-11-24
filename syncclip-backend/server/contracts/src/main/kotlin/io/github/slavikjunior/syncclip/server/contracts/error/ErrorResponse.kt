package io.github.slavikjunior.syncclip.server.contracts.error

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Тело ошибки API_SPEC `Error`; `details` — строковые пары (field/reason). */
@Serializable
data class ErrorResponse(
    @SerialName("error") val error: String,
    @SerialName("message") val message: String,
    @SerialName("details") val details: Map<String, String>? = null,
    @SerialName("retryAfter") val retryAfter: Long? = null,
)
