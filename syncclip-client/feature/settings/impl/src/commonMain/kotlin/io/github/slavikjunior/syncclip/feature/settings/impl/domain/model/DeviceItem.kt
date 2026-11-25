package io.github.slavikjunior.syncclip.feature.settings.impl.domain.model

internal data class DeviceItem(
    val id: String,
    val name: String,
    val icon: DeviceKind,
    val platform: String,
    val isCurrent: Boolean,
)
