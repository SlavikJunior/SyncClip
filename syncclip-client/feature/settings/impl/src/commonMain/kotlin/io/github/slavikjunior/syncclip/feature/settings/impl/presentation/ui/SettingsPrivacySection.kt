package io.github.slavikjunior.syncclip.feature.settings.impl.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.component.SettingsRow
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIconKind
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state.DeviceIntent
import org.jetbrains.compose.resources.stringResource
import syncclip.feature.settings.impl.generated.resources.Res
import syncclip.feature.settings.impl.generated.resources.settings_auto_clear_sensitive
import syncclip.feature.settings.impl.generated.resources.settings_auto_clear_sensitive_hint

/** Секция приватности: автоочистка чувствительных данных (US-021). */
@Composable
internal fun SettingsPrivacySection(
    autoClearSensitive: Boolean,
    onIntent: (DeviceIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        SettingsRow(
            title = stringResource(Res.string.settings_auto_clear_sensitive),
            subtitle = stringResource(Res.string.settings_auto_clear_sensitive_hint),
            icon = SyncClipIconKind.LOCK,
            trailing = {
                Switch(
                    checked = autoClearSensitive,
                    onCheckedChange = { onIntent(DeviceIntent.ToggleAutoClearSensitive) },
                )
            },
        )
    }
}

@Preview(widthDp = 420)
@Composable
private fun SettingsPrivacySectionPreview() {
    SyncClipTheme {
        SettingsPrivacySection(autoClearSensitive = true, onIntent = {})
    }
}
