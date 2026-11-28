package io.github.slavikjunior.syncclip.app.common.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import syncclip.app.common.generated.resources.Res
import syncclip.app.common.generated.resources.lock_failed
import syncclip.app.common.generated.resources.lock_hint
import syncclip.app.common.generated.resources.lock_title
import syncclip.app.common.generated.resources.lock_unlock
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIcon
import io.github.slavikjunior.syncclip.core.designsystem.component.SyncClipIconKind
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipSpacing
import io.github.slavikjunior.syncclip.core.designsystem.theme.SyncClipTheme
import io.github.slavikjunior.syncclip.core.platform.BiometricAuthenticator
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Экран биометрической блокировки (US-022).
 *
 * Stateless часть — [BiometricLockContent]; навигации и ViewModel здесь нет:
 * экран живёт в composition root рядом с экраном E2EE-разблокировки.
 */
@Composable
internal fun BiometricLockScreen(
    authenticator: BiometricAuthenticator,
    modifier: Modifier = Modifier,
) {
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val title = stringResource(Res.string.lock_title)
    val hint = stringResource(Res.string.lock_hint)
    val cancel = stringResource(Res.string.lock_title)

    val requestUnlock: () -> Unit = {
        scope.launch {
            // Отказ или ошибка платформы не разблокируют приложение: повтор —
            // единственный путь, иначе блокировка обходится кнопкой «назад».
            val unlocked = runCatching {
                authenticator.authenticate(title = title, subtitle = hint, cancelLabel = cancel)
            }.getOrDefault(false)
            failed = !unlocked
        }
    }

    LaunchedEffect(authenticator) { requestUnlock() }

    BiometricLockContent(
        failed = failed,
        onUnlock = requestUnlock,
        modifier = modifier,
    )
}

@Composable
internal fun BiometricLockContent(
    failed: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(SyncClipSpacing.screen),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SyncClipSpacing.md),
            ) {
                SyncClipIcon(
                    kind = SyncClipIconKind.LOCK,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 40.dp,
                )
                Text(
                    text = stringResource(Res.string.lock_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(if (failed) Res.string.lock_failed else Res.string.lock_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onUnlock, modifier = Modifier.size(width = 200.dp, height = 44.dp)) {
                    Text(text = stringResource(Res.string.lock_unlock))
                }
            }
        }
    }
}

@Preview
@Composable
private fun BiometricLockContentPreview() {
    SyncClipTheme {
        BiometricLockContent(failed = true, onUnlock = {})
    }
}
