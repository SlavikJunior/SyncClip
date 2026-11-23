package io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state

import org.jetbrains.compose.resources.StringResource

internal sealed interface DeviceEffect {

    data class ShowMessage(val message: StringResource) : DeviceEffect
}
