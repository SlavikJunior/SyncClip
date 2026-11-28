package io.github.slavikjunior.syncclip.app.ios.security

import io.github.slavikjunior.syncclip.core.platform.BiometricAuthenticator
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.timeIntervalSince1970
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import kotlin.coroutines.resume

/**
 * Биометрическая блокировка iOS (US-022).
 *
 * `LocalAuthentication` с политикой `LAPolicyDeviceOwnerAuthentication`: Face ID
 * или Touch ID, а при их отсутствии — код-пароль устройства. iOS не отдаёт
 * приложению «фоновое» время напрямую, поэтому используется отметка
 * `UIApplicationDidEnterBackgroundNotification`.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosBiometricAuthenticator : BiometricAuthenticator {

    private val _locked = MutableStateFlow(false)
    override val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var enabled: Boolean = false
    private var configuredOnce: Boolean = false
    private var timeoutMinutes: Int = DEFAULT_TIMEOUT_MINUTES
    private var backgroundedAt: Double = 0.0

    init {
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            if (enabled) backgroundedAt = NSDate().timeIntervalSince1970
        }
        center.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            if (!enabled) return@addObserverForName
            if (backgroundedAt <= 0.0) return@addObserverForName
            val elapsed = NSDate().timeIntervalSince1970 - backgroundedAt
            val timeout = if (timeoutMinutes == 0) 0.0 else timeoutMinutes * 60.0
            if (elapsed >= timeout) _locked.value = true
        }
    }

    override val isAvailable: Boolean
        get() = LAContext().canEvaluatePolicy(
            policy = LAPolicyDeviceOwnerAuthentication,
            error = null,
        )

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
        // Запираем только при первом применении настройки в процессе: включение
        // тумблера не должно сразу требовать Face ID, блокировка сработает при
        // следующем возврате из фона.
        if (firstConfigure) {
            backgroundedAt = NSDate().timeIntervalSince1970
            _locked.value = true
        }
    }

    override suspend fun authenticate(
        title: String,
        subtitle: String,
        cancelLabel: String,
    ): Boolean {
        val context = LAContext()
        if (!context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)) return false
        val confirmed = suspendCancellableCoroutine { continuation ->
            context.evaluatePolicy(
                policy = LAPolicyDeviceOwnerAuthentication,
                localizedReason = "$title. $subtitle",
            ) { success, _ ->
                continuation.resume(success)
            }
        }
        if (confirmed) _locked.value = false
        return confirmed
    }

    override fun lock() {
        if (enabled) _locked.value = true
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MINUTES = 5
    }
}
