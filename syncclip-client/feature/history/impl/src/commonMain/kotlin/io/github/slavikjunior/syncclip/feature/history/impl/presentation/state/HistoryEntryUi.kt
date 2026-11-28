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
    val isFavorite: Boolean = false,
    /** US-021: когда запись удалится автоматически; `null` — обычная запись. */
    val expiresAtMillis: Long? = null,
)

/** Устройство в диалоге адресной отправки (US-016). */
internal data class SendTargetUi(
    val id: String,
    val name: String,
    val icon: DeviceIcon,
)

/** Состояние диалога «Отправить на устройства». */
internal data class SendDialogUiState(
    val targets: List<SendTargetUi> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
) {
    val canSend: Boolean
        get() = selectedIds.isNotEmpty() && !isSending && !isLoading
}
