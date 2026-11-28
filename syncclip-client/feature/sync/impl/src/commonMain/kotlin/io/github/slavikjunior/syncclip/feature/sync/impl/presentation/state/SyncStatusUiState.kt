package io.github.slavikjunior.syncclip.feature.sync.impl.presentation.state

import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncConnectionStatus

internal data class SyncStatusUiState(
    val isOnline: Boolean = true,
    val autoInsert: Boolean = true,
    val lastText: String = "",
    val packetCount: Int = 0,
    val devices: List<SyncDeviceUi> = emptyList(),
    val isLoadingDevices: Boolean = false,
    /**
     * Состояние WebSocket-соединения (FR-2.3).
     *
     * По умолчанию [SyncConnectionStatus.ONLINE]: до первого события транспорта
     * экран не должен показывать тревогу. `SUSPENDED` — единственное состояние,
     * которое требует действия пользователя, поэтому оно и попадает в UI.
     */
    val connectionStatus: SyncConnectionStatus = SyncConnectionStatus.ONLINE,
)
