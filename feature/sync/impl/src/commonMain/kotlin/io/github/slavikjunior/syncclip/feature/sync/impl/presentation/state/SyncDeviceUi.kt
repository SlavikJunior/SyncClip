package io.github.slavikjunior.syncclip.feature.sync.impl.presentation.state

import io.github.slavikjunior.syncclip.shared.model.DeviceIcon

/** Устройство в списке синхронизации. */
internal data class SyncDeviceUi(
    val id: String,
    val name: String,
    val icon: DeviceIcon,
    val isCurrent: Boolean = false,
)
