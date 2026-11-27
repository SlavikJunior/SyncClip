package io.github.slavikjunior.syncclip.app.common.ui.e2ee

import kotlin.test.Test
import kotlin.test.assertEquals

/** Таймер блокировки читается одинаково: mm:ss до часа, дальше h:mm:ss. */
internal class LockoutFormatTest {

    @Test
    fun `minutes and seconds`() {
        assertEquals("05:00", formatLockout(300))
        assertEquals("00:59", formatLockout(59))
        assertEquals("00:00", formatLockout(0))
        assertEquals("00:00", formatLockout(-5))
    }

    @Test
    fun `hours are shown for the 2 hour lock`() {
        assertEquals("2:00:00", formatLockout(7_200))
        assertEquals("1:05:09", formatLockout(3_909))
    }
}
