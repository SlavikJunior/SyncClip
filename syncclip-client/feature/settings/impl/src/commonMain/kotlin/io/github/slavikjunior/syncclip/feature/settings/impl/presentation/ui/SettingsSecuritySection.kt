package io.github.slavikjunior.syncclip.feature.settings.impl.presentation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.component.SettingsRow
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIconKind
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipSpacing
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.feature.settings.impl.presentation.state.DeviceIntent
import org.jetbrains.compose.resources.stringResource
import syncclip.feature.settings.impl.generated.resources.Res
import syncclip.feature.settings.impl.generated.resources.settings_biometric_lock
import syncclip.feature.settings.impl.generated.resources.settings_biometric_lock_hint
import syncclip.feature.settings.impl.generated.resources.settings_biometric_timeout
import syncclip.feature.settings.impl.generated.resources.settings_biometric_timeout_immediate
import syncclip.feature.settings.impl.generated.resources.settings_biometric_timeout_minutes
import syncclip.feature.settings.impl.generated.resources.settings_biometric_unavailable

/** Допустимые значения «блокировать после», минуты (0 — сразу). */
private val BIOMETRIC_TIMEOUTS = listOf(0, 1, 5, 15)

/**
 * Секция безопасности: биометрическая блокировка (US-022).
 *
 * Если на устройстве нет биометрии, переключатель недоступен и показано
 * предупреждение — включать настройку, которая не сработает, нельзя.
 */
@Composable
internal fun SettingsSecuritySection(
    biometricAvailable: Boolean,
    biometricLock: Boolean,
    timeoutMinutes: Int,
    onIntent: (DeviceIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column {
            SettingsRow(
                title = stringResource(Res.string.settings_biometric_lock),
                subtitle = if (biometricAvailable) {
                    stringResource(Res.string.settings_biometric_lock_hint)
                } else {
                    stringResource(Res.string.settings_biometric_unavailable)
                },
                icon = SyncClipIconKind.LOCK,
                trailing = {
                    Switch(
                        checked = biometricLock && biometricAvailable,
                        enabled = biometricAvailable,
                        onCheckedChange = { onIntent(DeviceIntent.ToggleBiometricLock) },
                    )
                },
            )
            if (biometricAvailable && biometricLock) {
                Text(
                    text = stringResource(Res.string.settings_biometric_timeout),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = SyncClipSpacing.md,
                        end = SyncClipSpacing.md,
                        bottom = SyncClipSpacing.xs,
                    ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SyncClipSpacing.md)
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.surfaceVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BIOMETRIC_TIMEOUTS.forEach { minutes ->
                        val selected = minutes == timeoutMinutes
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) scheme.surface else Color.Transparent)
                                .clickable {
                                    onIntent(DeviceIntent.SetBiometricTimeout(minutes))
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = TimeoutLabel(minutes),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier.height(SyncClipSpacing.sm),
                )
            }
        }
    }
}

@Composable
private fun TimeoutLabel(minutes: Int): String = if (minutes == 0) {
    stringResource(Res.string.settings_biometric_timeout_immediate)
} else {
    stringResource(Res.string.settings_biometric_timeout_minutes, minutes)
}

@Preview(widthDp = 420)
@Composable
private fun SettingsSecuritySectionPreview() {
    SyncClipTheme {
        SettingsSecuritySection(
            biometricAvailable = true,
            biometricLock = true,
            timeoutMinutes = 5,
            onIntent = {},
        )
    }
}
