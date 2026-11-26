package io.github.slavikjunior.syncclip.core.clipboard

import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Очередь Share Extension: расширение пишет массив строк в App Group,
 * приложение забирает его ровно один раз.
 */
internal class IosPendingShareSourceTest {

    private val suite = "group.io.github.slavikjunior.syncclip"
    private val key = "pendingShares"

    @Test
    fun `queued texts are taken once`() {
        val defaults = NSUserDefaults(suiteName = suite)
        defaults.removeObjectForKey(key)
        defaults.setObject(listOf("first", "second"), forKey = key)

        val source = createPendingShareSource()

        assertEquals(listOf("first", "second"), source.take())
        assertTrue(source.take().isEmpty(), "очередь должна очищаться после чтения")
        defaults.removeObjectForKey(key)
    }

    @Test
    fun `already sent texts are drained separately`() {
        val defaults = NSUserDefaults(suiteName = suite)
        defaults.setObject(listOf("sent-1"), forKey = "sentShares")

        val source = createPendingShareSource()

        assertEquals(listOf("sent-1"), source.takeAlreadySent())
        assertTrue(source.takeAlreadySent().isEmpty())
        assertTrue(source.take().isEmpty(), "sentShares не попадают в неотправленные")
        defaults.removeObjectForKey("sentShares")
    }

    @Test
    fun `empty queue returns empty list`() {
        NSUserDefaults(suiteName = suite).removeObjectForKey(key)

        assertTrue(createPendingShareSource().take().isEmpty())
    }
}
