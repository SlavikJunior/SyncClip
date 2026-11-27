package io.github.slavikjunior.syncclip.app.desktop

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent

/**
 * Какая комбинация открывает окно истории.
 *
 * Passport FR-3.1: `Ctrl+Shift+V` на Windows/Linux и `Cmd+Shift+V` на macOS.
 */
internal object HotkeyMatcher {

    fun isOpenHistory(modifiers: Int, keyCode: Int): Boolean {
        if (keyCode != NativeKeyEvent.VC_V) return false
        if (modifiers and NativeKeyEvent.SHIFT_MASK == 0) return false
        val control = modifiers and NativeKeyEvent.CTRL_MASK != 0
        val command = modifiers and NativeKeyEvent.META_MASK != 0
        return control || command
    }
}
