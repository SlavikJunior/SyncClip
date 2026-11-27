package io.github.slavikjunior.syncclip.app.desktop

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyAdapter
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener

/**
 * Глобальный хоткей через JNativeHook.
 *
 * JVM не умеет слушать клавиатуру вне окна без нативной библиотеки; JNativeHook
 * — минимальное решение для Windows/macOS/Linux. На macOS требуется разрешение
 * «Мониторинг ввода»/Accessibility: без него `start()` вернёт `false`, а трей и
 * остальное приложение продолжат работать.
 */
internal class GlobalHotkey(
    private val onTrigger: () -> Unit,
) {

    private var listener: NativeKeyListener? = null
    private var registered = false

    fun start(): Boolean {
        if (registered) return true
        return runCatching {
            GlobalScreen.registerNativeHook()
            val keyListener = object : NativeKeyAdapter() {
                override fun nativeKeyPressed(event: NativeKeyEvent) {
                    if (HotkeyMatcher.isOpenHistory(event.modifiers, event.keyCode)) {
                        onTrigger()
                    }
                }
            }
            GlobalScreen.addNativeKeyListener(keyListener)
            listener = keyListener
            registered = true
            true
        }.getOrDefault(false)
    }

    fun stop() {
        listener?.let { GlobalScreen.removeNativeKeyListener(it) }
        listener = null
        if (registered) {
            runCatching { GlobalScreen.unregisterNativeHook() }
            registered = false
        }
    }
}
