package io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state

import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettings
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
    val autoStartSupported: Boolean = false,
    val autoStartEnabled: Boolean = false,
    val isAutoStartBusy: Boolean = false,
    val maxEntriesInput: String = ClipboardSettings.DEFAULT_MAX_ENTRIES.toString(),
    val ttlHoursInput: String = ClipboardSettings.DEFAULT_TTL_HOURS.toString(),
    val maxBytesMbInput: String = (ClipboardSettings.DEFAULT_MAX_BYTES / MEBIBYTE).toString(),
    val limitsError: StringResource? = null,
    val isLimitsSaving: Boolean = false,
) {
    private companion object {
        const val MEBIBYTE = 1024L * 1024L
    }
}
