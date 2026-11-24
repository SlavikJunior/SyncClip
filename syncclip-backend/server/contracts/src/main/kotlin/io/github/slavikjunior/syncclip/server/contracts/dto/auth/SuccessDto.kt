package io.github.slavikjunior.syncclip.server.contracts.dto.auth

import kotlinx.serialization.Serializable

@Serializable
data class SuccessDto(val success: Boolean = true)
