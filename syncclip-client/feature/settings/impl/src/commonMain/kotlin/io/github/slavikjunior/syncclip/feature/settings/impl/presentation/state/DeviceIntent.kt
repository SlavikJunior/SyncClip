package io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state

import io.github.slavikjunior.syncclip.feature.settings.impl.domain.model.ArchiveFormat
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

    /** Автозапуск Desktop (FR-3.1); на Android/iOS намерение игнорируется. */
    data object ToggleAutoStart : DeviceIntent

    data class EntriesChanged(val value: String) : DeviceIntent

    data class TtlHoursChanged(val value: String) : DeviceIntent

    data class MaxBytesMbChanged(val value: String) : DeviceIntent

    /** FR-4.3: применить все три лимита истории. */
    data object SaveHistoryLimits : DeviceIntent

    /** US-009: переключатель в секции уведомлений. */
    data class ToggleNotification(val kind: NotificationKind) : DeviceIntent

    /** US-021. */
    data object ToggleAutoClearSensitive : DeviceIntent

    /** US-022: включение/выключение биометрической блокировки. */
    data object ToggleBiometricLock : DeviceIntent

    data class SetBiometricTimeout(val minutes: Int) : DeviceIntent

    /** US-018: диалог выбора формата экспорта. */
    data object OpenExportDialog : DeviceIntent

    data object DismissExportDialog : DeviceIntent

    data class ExportHistory(val format: ArchiveFormat) : DeviceIntent

    /** US-024: выбрать файл и импортировать записи. */
    data object ImportHistory : DeviceIntent
}

/** Какой тумблер уведомлений переключили. */
internal enum class NotificationKind {
    NEW_COPIES,
    NEW_DEVICE,
    FAILED_SIGN_IN,
    SOUND,
    VIBRATION,
}
