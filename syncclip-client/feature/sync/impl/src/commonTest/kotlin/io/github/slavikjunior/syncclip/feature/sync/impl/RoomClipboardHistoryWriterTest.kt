package io.github.slavikjunior.syncclip.feature.sync.impl

import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettings
import io.github.slavikjunior.syncclip.core.database.ClipboardEventEntity
import io.github.slavikjunior.syncclip.feature.sync.impl.data.repository.RoomClipboardHistoryWriter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class RoomClipboardHistoryWriterTest {

    private val dao = FakeClipboardEventDao()
    private val clock = TestClock()
    private var ids = 0

    private fun writer(settings: ClipboardSettings): RoomClipboardHistoryWriter =
        RoomClipboardHistoryWriter(
            dao = dao,
            clock = clock,
            settings = FakeClipboardSettingsStore(settings),
            newId = { "id-${ids++}" },
        )

    private fun defaultSettings(
        maxEntries: Int = 200,
        maxBytes: Long = 10L * 1024 * 1024,
    ) = ClipboardSettings(maxEntries = maxEntries, ttlHours = 7 * 24, maxBytes = maxBytes)

    @Test
    fun `saved entry is marked as local`() = runTest {
        writer(defaultSettings()).save("hello", clock.nowMillis(), sourceDeviceId = null)

        val entry = dao.entities.single()
        assertEquals("hello", entry.text)
        assertEquals(null, entry.sourceDeviceId)
        assertEquals(false, entry.isPinned)
    }

    @Test
    fun `count limit evicts the oldest unpinned entry`() = runTest {
        val writer = writer(defaultSettings(maxEntries = 2))

        writer.save("one", clock.nowMillis(), null)
        clock.advance(1)
        writer.save("two", clock.nowMillis(), null)
        clock.advance(1)
        writer.save("three", clock.nowMillis(), null)

        assertEquals(listOf("two", "three"), dao.entities.map { it.text })
    }

    @Test
    fun `pinned entry survives count limit`() = runTest {
        val writer = writer(defaultSettings(maxEntries = 2))
        writer.save("pinned", clock.nowMillis(), null)
        dao.entities.first().let { dao.setPinned(it.id, true) }
        clock.advance(1)
        writer.save("two", clock.nowMillis(), null)
        clock.advance(1)
        writer.save("three", clock.nowMillis(), null)

        assertEquals(listOf("pinned", "three"), dao.entities.map { it.text }.sorted())
    }

    @Test
    fun `expired unpinned entry is removed on save`() = runTest {
        dao.upsert(
            ClipboardEventEntity(
                id = "old",
                text = "stale",
                timestampMillis = clock.nowMillis() - TTL_MILLIS - 1,
                sourceDeviceId = null,
                isPinned = false,
            ),
        )

        writer(defaultSettings()).save("fresh", clock.nowMillis(), null)

        assertEquals(listOf("fresh"), dao.entities.map { it.text })
    }

    @Test
    fun `byte limit keeps storage under the threshold`() = runTest {
        val writer = writer(defaultSettings(maxBytes = 59))

        writer.save("a".repeat(20), clock.nowMillis(), null)
        clock.advance(1)
        writer.save("b".repeat(20), clock.nowMillis(), null)
        clock.advance(1)
        writer.save("c".repeat(20), clock.nowMillis(), null)

        assertTrue(dao.totalBytes() <= 59)
        assertEquals(listOf("b".repeat(20), "c".repeat(20)), dao.entities.map { it.text })
    }

    @Test
    fun `changing limits applies to the next save`() = runTest {
        val settings = FakeClipboardSettingsStore(ClipboardSettings(maxEntries = 200))
        val writer = RoomClipboardHistoryWriter(
            dao = dao,
            clock = clock,
            settings = settings,
            newId = { "id-${ids++}" },
        )
        writer.save("one", clock.nowMillis(), null)
        clock.advance(1)
        writer.save("two", clock.nowMillis(), null)

        settings.update(ClipboardSettings(maxEntries = 1))
        clock.advance(1)
        writer.save("three", clock.nowMillis(), null)

        assertEquals(listOf("three"), dao.entities.map { it.text })
    }

    private companion object {
        const val TTL_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
