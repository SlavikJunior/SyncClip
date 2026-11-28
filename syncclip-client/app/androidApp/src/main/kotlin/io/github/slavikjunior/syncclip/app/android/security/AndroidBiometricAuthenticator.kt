package io.github.slavikjunior.syncclip.app.android.security

import android.app.Activity
import android.app.KeyguardManager
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.github.slavikjunior.syncclip.core.platform.BiometricAuthenticator
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Биометрическая блокировка Android (US-022).
 *
 * Использует системное подтверждение личности (`KeyguardManager`): на устройстве
 * с настроенной биометрией система сама показывает сканер лица/отпечатка, а при
 * его отсутствии — PIN/графический ключ. Отдельная зависимость `androidx.biometric`
 * не нужна, поэтому поведение одинаково на API 26+.
 *
 * Таймаут отсчитывается от ухода приложения в фон: `onStop` запоминает время,
 * `onStart` решает, блокировать ли экран.
 */
internal class AndroidBiometricAuthenticator(
    private val activity: ComponentActivity,
) : BiometricAuthenticator, DefaultLifecycleObserver {

    private val keyguard: KeyguardManager? =
        activity.getSystemService(KeyguardManager::class.java)

    private val _locked = MutableStateFlow(false)
    override val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var enabled: Boolean = false
    private var configuredOnce: Boolean = false
    private var timeoutMinutes: Int = DEFAULT_TIMEOUT_MINUTES
    private var backgroundedAtMillis: Long = 0L
    private var pending: CancellableContinuation<Boolean>? = null

    private val confirmLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        pending?.let { continuation ->
            pending = null
            val confirmed = result.resultCode == Activity.RESULT_OK
            if (confirmed) _locked.value = false
            continuation.resume(confirmed)
        }
    }

    init {
        activity.lifecycle.addObserver(this)
    }

    override val isAvailable: Boolean
        get() = keyguard?.isDeviceSecure == true

    override fun configure(enabled: Boolean, timeoutMinutes: Int) {
        val target = enabled && isAvailable
        val firstConfigure = !configuredOnce
        this.enabled = target
        this.timeoutMinutes = timeoutMinutes
        configuredOnce = true
        if (!target) {
            _locked.value = false
            return
        }
        // Запираем только при первом применении настройки в процессе: повторный
        // `configure` (открытие настроек) и включение тумблера не должны
        // запирать уже разблокированное приложение — блокировка сработает при
        // следующем возврате из фона.
        if (firstConfigure) {
            backgroundedAtMillis = SystemClock.elapsedRealtime()
            _locked.value = true
        }
    }

    override suspend fun authenticate(
        title: String,
        subtitle: String,
        cancelLabel: String,
    ): Boolean {
        val manager = keyguard ?: return false
        if (!manager.isDeviceSecure) return false
        val intent = manager.createConfirmDeviceCredentialIntent(title, subtitle) ?: return false
        val confirmed = suspendCancellableCoroutine { continuation ->
            pending = continuation
            continuation.invokeOnCancellation { pending = null }
            confirmLauncher.launch(intent)
        }
        return confirmed
    }

    override fun lock() {
        if (enabled) _locked.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        if (enabled) backgroundedAtMillis = SystemClock.elapsedRealtime()
    }

    override fun onStart(owner: LifecycleOwner) {
        if (!enabled || !isAvailable) return
        if (backgroundedAtMillis == 0L) return
        val elapsed = SystemClock.elapsedRealtime() - backgroundedAtMillis
        val timeoutMillis = if (timeoutMinutes == 0) 0L else timeoutMinutes * MINUTE_MILLIS
        if (elapsed >= timeoutMillis) {
            _locked.value = true
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MINUTES = 5
        const val MINUTE_MILLIS = 60_000L
    }
}
