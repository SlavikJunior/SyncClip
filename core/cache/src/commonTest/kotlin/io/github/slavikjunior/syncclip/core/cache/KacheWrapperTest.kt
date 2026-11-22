package io.github.slavikjunior.syncclip.core.cache

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

internal class KacheWrapperTest {

    private val clock = MutableClock()

    @Test
    fun `value is readable within ttl`() = runTest {
        val cache = KacheWrapper<String, String>(maxSize = 4, defaultTtl = 1.minutes, clock = clock)

        cache.put("k", "v")
        clock.advance(59_999.milliseconds)

        assertEquals("v", cache.get("k")?.value)
    }

    @Test
    fun `value expires strictly after ttl`() = runTest {
        val cache = KacheWrapper<String, String>(maxSize = 4, defaultTtl = 1.minutes, clock = clock)

        cache.put("k", "v")
        clock.advance(1.minutes)
        assertNotNull(cache.get("k"), "на границе TTL запись ещё свежая")

        clock.advance(1.milliseconds)
        assertNull(cache.get("k"))
    }

    @Test
    fun `put without ttl uses cache default`() = runTest {
        val cache = KacheWrapper<String, String>(maxSize = 4, defaultTtl = 10.milliseconds, clock = clock)

        cache.put("k", "v")
        clock.advance(11.milliseconds)

        assertNull(cache.get("k"))
    }

    @Test
    fun `explicit ttl overrides default`() = runTest {
        val cache = KacheWrapper<String, String>(maxSize = 4, defaultTtl = 1.minutes, clock = clock)

        cache.put("k", "v", ttl = 10.milliseconds)
        clock.advance(20.milliseconds)

        assertNull(cache.get("k"))
    }

    @Test
    fun `cache without default ttl keeps entries forever`() = runTest {
        val cache = KacheWrapper<String, String>(maxSize = 4, defaultTtl = null, clock = clock)

        cache.put("k", "v")
        clock.advance(365.minutes)

        assertEquals("v", cache.get("k")?.value)
    }

    @Test
    fun `stale value is served by getStale only`() = runTest {
        val cache = KacheWrapper<String, String>(maxSize = 4, defaultTtl = 10.milliseconds, clock = clock)

        cache.put("k", "v")
        clock.advance(11.milliseconds)

        assertEquals("v", cache.getStale("k")?.value)
        assertNull(cache.get("k"), "get удаляет просроченную запись")
        assertNull(cache.getStale("k"))
    }

    @Test
    fun `oldest entry is evicted when maxSize is exceeded`() = runTest {
        val cache = KacheWrapper<String, String>(maxSize = 2)

        cache.put("a", "1")
        cache.put("b", "2")
        cache.put("c", "3")

        assertNull(cache.get("a"))
        assertEquals("2", cache.get("b")?.value)
        assertEquals("3", cache.get("c")?.value)
        assertEquals(2, cache.size())
    }

    @Test
    fun `removeExpired drops only expired entries`() = runTest {
        val cache = KacheWrapper<String, String>(maxSize = 4, defaultTtl = 10.milliseconds, clock = clock)

        cache.put("old", "1")
        clock.advance(11.milliseconds)
        cache.put("fresh", "2")

        assertEquals(1, cache.removeExpired())
        assertEquals("2", cache.get("fresh")?.value)
        assertEquals(1, cache.size())
    }

    @Test
    fun `remove and clear drop entries`() = runTest {
        val cache = KacheWrapper<String, String>(maxSize = 4)

        cache.put("a", "1")
        cache.put("b", "2")
        cache.remove("a")

        assertNull(cache.get("a"))
        assertTrue(cache.size() == 1)

        cache.clear()
        assertEquals(0, cache.size())
    }
}
