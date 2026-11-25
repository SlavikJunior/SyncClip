package io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state

import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceKind

internal sealed interface DeviceIntent {

    data object Load : DeviceIntent

    data object RetryPending : DeviceIntent

    data class OpenRename(val deviceId: String) : DeviceIntent

    data class NameChanged(val name: String) : DeviceIntent

    data class IconSelected(val icon: DeviceKind) : DeviceIntent

    data object Save : DeviceIntent

    data object DismissDialog : DeviceIntent

    data object DismissError : DeviceIntent
}
