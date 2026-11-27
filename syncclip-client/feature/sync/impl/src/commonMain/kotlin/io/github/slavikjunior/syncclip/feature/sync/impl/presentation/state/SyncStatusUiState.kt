package io.github.slavikjunior.syncclip.feature.sync.impl.presentation.state

internal data class SyncStatusUiState(
    val isOnline: Boolean = true,
    val autoInsert: Boolean = true,
    val lastText: String = "",
    val packetCount: Int = 0,
    val devices: List<SyncDeviceUi> = emptyList(),
    val isLoadingDevices: Boolean = false,
)
