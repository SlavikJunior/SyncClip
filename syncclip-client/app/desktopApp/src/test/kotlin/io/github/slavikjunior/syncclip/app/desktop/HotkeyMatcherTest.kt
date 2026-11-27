package io.github.slavikjunior.syncclip.app.desktop

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** FR-3.1: Ctrl/Cmd+Shift+V открывает историю, остальные комбинации — нет. */
internal class HotkeyMatcherTest {

    private val shiftCtrl = NativeKeyEvent.SHIFT_MASK or NativeKeyEvent.CTRL_MASK
    private val shiftMeta = NativeKeyEvent.SHIFT_MASK or NativeKeyEvent.META_MASK

    @Test
    fun `ctrl shift v opens history`() {
        assertTrue(HotkeyMatcher.isOpenHistory(shiftCtrl, NativeKeyEvent.VC_V))
    }

    @Test
    fun `cmd shift v opens history on mac`() {
        assertTrue(HotkeyMatcher.isOpenHistory(shiftMeta, NativeKeyEvent.VC_V))
    }

    @Test
    fun `other combinations are ignored`() {
        assertFalse(HotkeyMatcher.isOpenHistory(NativeKeyEvent.CTRL_MASK, NativeKeyEvent.VC_V))
        assertFalse(HotkeyMatcher.isOpenHistory(NativeKeyEvent.SHIFT_MASK, NativeKeyEvent.VC_V))
        assertFalse(HotkeyMatcher.isOpenHistory(shiftCtrl, NativeKeyEvent.VC_C))
        assertFalse(HotkeyMatcher.isOpenHistory(shiftCtrl, NativeKeyEvent.VC_V.toInt() + 1))
    }
}
