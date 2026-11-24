package io.github.slavikjunior.syncclip.server.contracts.dto.auth

import kotlinx.serialization.Serializable

@Serializable
data class CanaryDto(val encryptedCanary: String)
