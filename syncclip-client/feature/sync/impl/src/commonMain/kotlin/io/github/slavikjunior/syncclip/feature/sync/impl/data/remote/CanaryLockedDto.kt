package io.github.slavikjunior.syncclip.feature.sync.impl.data.remote

import kotlinx.serialization.Serializable

/** Тело `403 master_password_locked`: сколько секунд ждать (дубль Retry-After). */
@Serializable
internal data class CanaryLockedDto(
    val error: String = "",
    val message: String = "",
    val retryAfter: Long? = null,
)
