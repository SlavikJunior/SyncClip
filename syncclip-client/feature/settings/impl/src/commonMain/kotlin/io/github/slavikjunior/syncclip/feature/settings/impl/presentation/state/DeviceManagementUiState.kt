package io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state

import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceItem
import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.DeviceKind
import org.jetbrains.compose.resources.StringResource

internal data class DeviceManagementUiState(
    val devices: List<DeviceItem> = emptyList(),
    val isLoading: Boolean = false,
    val pendingCount: Int = 0,
    val editingDeviceId: String? = null,
    val draftName: String = "",
    val draftIcon: DeviceKind = DeviceKind.DESKTOP,
    val error: StringResource? = null,
)
