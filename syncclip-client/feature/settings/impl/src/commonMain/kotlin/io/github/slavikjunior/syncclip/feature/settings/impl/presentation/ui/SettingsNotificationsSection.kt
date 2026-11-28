package io.github.slavikjunior.syncclip.feature.settings.impl.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.datastore.NotificationSettings
import io.github.slavikjunior.syncclip.core.designsystem.component.SettingsRow
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIconKind
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state.DeviceIntent
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state.NotificationKind
import org.jetbrains.compose.resources.stringResource
import syncclip.feature.settings.impl.generated.resources.Res
import syncclip.feature.settings.impl.generated.resources.settings_notify_failed_sign_in
import syncclip.feature.settings.impl.generated.resources.settings_notify_failed_sign_in_hint
import syncclip.feature.settings.impl.generated.resources.settings_notify_new_copies
import syncclip.feature.settings.impl.generated.resources.settings_notify_new_copies_hint
import syncclip.feature.settings.impl.generated.resources.settings_notify_new_device
import syncclip.feature.settings.impl.generated.resources.settings_notify_new_device_hint
import syncclip.feature.settings.impl.generated.resources.settings_notify_sound
import syncclip.feature.settings.impl.generated.resources.settings_notify_vibration

/**
 * Секция уведомлений (US-009): что показывать и как.
 *
 * Настройки локальные: сервер отправляет push всегда, клиент решает,
 * показывать ли его (и с каким звуком).
 */
@Composable
internal fun SettingsNotificationsSection(
    notifications: NotificationSettings,
    onIntent: (DeviceIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            NotificationToggle(
                title = stringResource(Res.string.settings_notify_new_copies),
                subtitle = stringResource(Res.string.settings_notify_new_copies_hint),
                icon = SyncClipIconKind.CLIPBOARD,
                checked = notifications.newCopies,
                onToggle = { onIntent(DeviceIntent.ToggleNotification(NotificationKind.NEW_COPIES)) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NotificationToggle(
                title = stringResource(Res.string.settings_notify_new_device),
                subtitle = stringResource(Res.string.settings_notify_new_device_hint),
                icon = SyncClipIconKind.DEVICES,
                checked = notifications.newDevice,
                onToggle = { onIntent(DeviceIntent.ToggleNotification(NotificationKind.NEW_DEVICE)) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NotificationToggle(
                title = stringResource(Res.string.settings_notify_failed_sign_in),
                subtitle = stringResource(Res.string.settings_notify_failed_sign_in_hint),
                icon = SyncClipIconKind.LOCK,
                checked = notifications.failedSignIn,
                onToggle = {
                    onIntent(DeviceIntent.ToggleNotification(NotificationKind.FAILED_SIGN_IN))
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NotificationToggle(
                title = stringResource(Res.string.settings_notify_sound),
                icon = SyncClipIconKind.SYNC,
                checked = notifications.sound,
                onToggle = { onIntent(DeviceIntent.ToggleNotification(NotificationKind.SOUND)) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NotificationToggle(
                title = stringResource(Res.string.settings_notify_vibration),
                icon = SyncClipIconKind.PHONE,
                checked = notifications.vibration,
                onToggle = { onIntent(DeviceIntent.ToggleNotification(NotificationKind.VIBRATION)) },
            )
        }
    }
}

@Composable
private fun NotificationToggle(
    title: String,
    icon: SyncClipIconKind,
    checked: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        trailing = {
            Switch(checked = checked, onCheckedChange = { onToggle() })
        },
    )
}

@Preview(widthDp = 420)
@Composable
private fun SettingsNotificationsSectionPreview() {
    SyncClipTheme {
        SettingsNotificationsSection(
            notifications = NotificationSettings(newCopies = true, vibration = false),
            onIntent = {},
        )
    }
}
