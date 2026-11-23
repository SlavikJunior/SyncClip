package io.github.slavikjunior.syncclip.feature.settings.impl.data.mapper

import io.github.slavikjunior.syncclip.feature.settings.impl.data.remote.DeviceDto
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceItem
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceKind
import io.github.slavikjunior.syncclip.shared.model.DeviceIcon

internal fun DeviceDto.toDomain(): DeviceItem = DeviceItem(
    id = id,
    name = name,
    icon = runCatching { DeviceKind.valueOf(icon) }.getOrDefault(DeviceKind.DESKTOP),
    platform = platform,
    isCurrent = false,
)

internal fun DeviceKind.toTransport(): String =
    runCatching { DeviceIcon.valueOf(name) }.getOrDefault(DeviceIcon.DESKTOP).name
