package io.github.slavikjunior.syncclip.app.common.ui.e2ee

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.slavikjunior.syncclip.core.designsystem.preview.FormFactorPreviews
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipSpacing
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import org.jetbrains.compose.resources.stringResource
import syncclip.app.common.generated.resources.Res
import syncclip.app.common.generated.resources.e2ee_attempts_left
import syncclip.app.common.generated.resources.e2ee_error_network
import syncclip.app.common.generated.resources.e2ee_hint
import syncclip.app.common.generated.resources.e2ee_locked
import syncclip.app.common.generated.resources.e2ee_password_label
import syncclip.app.common.generated.resources.e2ee_subtitle
import syncclip.app.common.generated.resources.e2ee_title
import syncclip.app.common.generated.resources.e2ee_unlock

/**
 * Экран разблокировки E2EE (FR-1.2, FR-1.4).
 *
 * При активной блокировке кнопка выключена, а вместо ошибки показывается
 * обратный отсчёт: запрос на сервер в это время не отправляется.
 */
@Composable
internal fun E2eeUnlockContent(
    password: String,
    isSubmitting: Boolean,
    lockedSeconds: Long,
    remainingAttempts: Int?,
    error: String?,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    // Форма мастер-пароля не растягивается на всю ширину десктопа.
                    .widthIn(max = SyncClipSpacing.formMaxWidth)
                    .fillMaxHeight()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SyncClipSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(SyncClipSpacing.lg),
            ) {
                Text(
                    text = stringResource(Res.string.e2ee_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = SyncClipSpacing.xxl),
                )
                Text(
                    text = stringResource(Res.string.e2ee_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.e2ee_password_label)) },
                    singleLine = true,
                    enabled = !isSubmitting && lockedSeconds == 0L,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                )
                if (lockedSeconds > 0) {
                    Text(
                        text = stringResource(Res.string.e2ee_locked, formatLockout(lockedSeconds)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    if (error != null) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (remainingAttempts != null) {
                        Text(
                            text = stringResource(Res.string.e2ee_attempts_left, remainingAttempts),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Button(
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = password.length >= MIN_PASSWORD_LENGTH &&
                        !isSubmitting &&
                        lockedSeconds == 0L,
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(stringResource(Res.string.e2ee_unlock))
                }
                Text(
                    text = stringResource(Res.string.e2ee_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Минимальная длина мастер-пароля: PBKDF2 остаётся стойким даже к коротким. */
internal const val MIN_PASSWORD_LENGTH = 8

/** `mm:ss` или `h:mm:ss` — одинаково читается на всех платформах. */
internal fun formatLockout(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return if (hours > 0) {
        "$hours:${minutes.pad()}:${remaining.pad()}"
    } else {
        "${minutes.pad()}:${remaining.pad()}"
    }
}

private fun Long.pad(): String = toString().padStart(2, '0')

@FormFactorPreviews
@Composable
private fun E2eeUnlockContentPreview() {
    SyncClipTheme {
        E2eeUnlockContent(
            password = "correct horse",
            isSubmitting = false,
            lockedSeconds = 0,
            remainingAttempts = 2,
            error = stringResource(Res.string.e2ee_error_network),
            onPasswordChange = {},
            onSubmit = {},
        )
    }
}

@FormFactorPreviews
@Composable
private fun E2eeUnlockLockedPreview() {
    SyncClipTheme {
        E2eeUnlockContent(
            password = "",
            isSubmitting = false,
            lockedSeconds = 300,
            remainingAttempts = null,
            error = null,
            onPasswordChange = {},
            onSubmit = {},
        )
    }
}
