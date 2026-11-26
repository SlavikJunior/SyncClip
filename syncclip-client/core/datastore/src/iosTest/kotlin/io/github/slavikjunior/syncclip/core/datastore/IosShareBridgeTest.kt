package io.github.slavikjunior.syncclip.core.datastore

import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Зеркало сессии для Share Extension: те же ключи, что читает Swift
 * (`share.access_token`, `share.device_id`, `share.api_base_url`).
 */
internal class IosShareBridgeTest {

    private val suite = "group.io.github.slavikjunior.syncclip"

    private fun defaults() = NSUserDefaults(suiteName = suite)

    @Test
    fun `session tokens are published and cleared`() {
        val bridge = shareBridge()
        bridge.session("user-1", "access-1")

        assertEquals("user-1", defaults().stringForKey("share.user_id"))
        assertEquals("access-1", defaults().stringForKey("share.access_token"))

        bridge.session(null, null)
        assertNull(defaults().stringForKey("share.user_id"))
        assertNull(defaults().stringForKey("share.access_token"))
    }

    @Test
    fun `device and endpoint are published`() {
        shareBridge().device("device-9")
        shareBridge().endpoint("http://localhost:8080")

        assertEquals("device-9", defaults().stringForKey("share.device_id"))
        assertEquals("http://localhost:8080", defaults().stringForKey("share.api_base_url"))
    }
}
