package io.github.slavikjunior.syncclip.app.common.ui.e2ee

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.slavikjunior.syncclip.core.datastore.E2eeLockoutStore
import io.github.slavikjunior.syncclip.core.datastore.MasterPasswordLockout
import io.github.slavikjunior.syncclip.feature.sync.api.E2eeKeyProvisioner
import io.github.slavikjunior.syncclip.feature.sync.api.E2eeUnlockResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import syncclip.app.common.generated.resources.Res
import syncclip.app.common.generated.resources.e2ee_error_invalid
import syncclip.app.common.generated.resources.e2ee_error_network

/**
 * Держит форму разблокировки: PBKDF2 (100k итераций) выполняется в фоне,
 * лимиты попыток и таймер блокировки приходят из [E2eeLockoutStore].
 */
@Composable
internal fun E2eeUnlockScreen(
    provisioner: E2eeKeyProvisioner,
    lockoutStore: E2eeLockoutStore,
    onUnlocked: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    var password by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var nowMillis by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    val lockout by lockoutStore.state.collectAsState(initial = MasterPasswordLockout())
    val scope = rememberCoroutineScope()
    val invalidMessage = stringResource(Res.string.e2ee_error_invalid)
    val networkMessage = stringResource(Res.string.e2ee_error_network)

    val lockedSeconds = if (lockout.isLocked(nowMillis)) {
        lockout.remainingSeconds(nowMillis)
    } else {
        0L
    }

    // Тикаем только пока блокировка активна: запрос в это время не отправляется.
    LaunchedEffect(lockout.lockedUntilMillis) {
        nowMillis = Clock.System.now().toEpochMilliseconds()
        while (lockout.lockedUntilMillis > Clock.System.now().toEpochMilliseconds()) {
            delay(1_000)
            nowMillis = Clock.System.now().toEpochMilliseconds()
        }
    }

    E2eeUnlockContent(
        modifier = modifier,
        password = password,
        isSubmitting = isSubmitting,
        lockedSeconds = lockedSeconds,
        remainingAttempts = visibleRemainingAttempts(lockout, nowMillis),
        error = error,
        onPasswordChange = { value ->
            password = value
            error = null
        },
        onSubmit = {
            if (!isSubmitting && lockedSeconds == 0L) {
                isSubmitting = true
                error = null
                scope.launch {
                    val result = withContext(Dispatchers.Default) {
                        provisioner.unlock(password)
                    }
                    isSubmitting = false
                    when (result) {
                        is E2eeUnlockResult.Success -> {
                            password = ""
                            onUnlocked(result.key)
                        }

                        is E2eeUnlockResult.InvalidPassword -> {
                            // Остаток попыток уже в lockoutStore — второй строки не дублируем.
                            error = invalidMessage
                        }

                        is E2eeUnlockResult.Locked -> {
                            nowMillis = Clock.System.now().toEpochMilliseconds()
                        }

                        is E2eeUnlockResult.Unavailable -> error = networkMessage
                    }
                }
            }
        },
    )
}

/**
 * Сколько попыток показывать до ввода.
 *
 * `remainingAttempts = 0` означает активную блокировку: после её истечения
 * сервер начнёт новую группу, поэтому «Осталось попыток: 0» было бы враньём.
 */
internal fun visibleRemainingAttempts(
    lockout: MasterPasswordLockout,
    nowMillis: Long,
): Int? {
    if (lockout.isLocked(nowMillis)) return null
    return lockout.remainingAttempts
        .takeIf { it in 1 until MasterPasswordLockout.ATTEMPTS_PER_GROUP }
}
