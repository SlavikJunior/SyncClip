package io.github.slavikjunior.syncclip.feature.auth.impl.data.remote

import kotlinx.serialization.Serializable

/** Ошибка API (`docs/API_SPEC.yaml` → `Error`). */
@Serializable
internal data class ApiErrorDto(
    val error: String,
    val message: String = "",
)
