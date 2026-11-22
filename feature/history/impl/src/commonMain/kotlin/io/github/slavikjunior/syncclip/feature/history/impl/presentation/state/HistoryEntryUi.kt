package io.github.slavikjunior.syncclip.feature.history.impl.presentation.state

import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntryType
import io.github.slavikjunior.syncclip.shared.model.DeviceIcon

internal data class HistoryEntryUi(
    val id: String,
    val text: String,
    val timestampMillis: Long,
    val sourceDeviceId: String?,
    val isPinned: Boolean,
    val sourceDeviceName: String? = null,
    val sourceIcon: DeviceIcon? = null,
    val type: HistoryEntryType = HistoryEntryType.TEXT,
)

