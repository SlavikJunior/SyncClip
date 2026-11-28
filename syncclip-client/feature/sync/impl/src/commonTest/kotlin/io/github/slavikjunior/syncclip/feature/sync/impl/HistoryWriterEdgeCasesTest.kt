package io.github.slavikjunior.syncclip.feature.sync.impl

import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettings
import io.github.slavikjunior.syncclip.core.database.ClipboardEventEntity
import io.github.slavikjunior.syncclip.feature.sync.impl.data.repository.RoomClipboardHistoryWriter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Политика вытеснения на границах: всё закреплено, одна запись больше лимита,
 * лимит меньше числа закреплённых.
 *
 * Цикл вытеснения по объёму обязан завершаться даже когда удалять нечего —
 * иначе копирование вставало бы навсегда.
 */
internal class HistoryWriterEdgeCasesTest {

    private val dao = FakeClipboardEventDao()
    private val clock = TestClock()
    private var ids = 0

    private fun writer(settings: ClipboardSettings) = RoomClipboardHistoryWriter(
        dao = dao,
        clock = clock,
        settings = FakeClipboardSettingsStore(settings),
        newId = { "id-${ids++}" },
    )

    private fun settings(maxEntries: Int = 200, maxBytes: Long = 10L * 1024 * 1024) =
        ClipboardSettings(maxEntries = maxEntries, ttlHours = 7 * 24, maxBytes = maxBytes)

    @Test
    fun `all entries pinned above the byte limit are kept`() = runTest {
        dao.upsert(entity("p1", "a".repeat(50), pinned = true))
        dao.upsert(entity("p2", "b".repeat(50), pinned = true))

        // Лимит 60 байт, а закреплённые занимают 100: вытеснять нечего,
        // и цикл должен просто остановиться.
        writer(settings(maxBytes = 60)).save("fresh", clock.nowMillis(), null)

        assertTrue(dao.entities.any { it.id == "p1" })
        assertTrue(dao.entities.any { it.id == "p2" })
    }

    @Test
    fun `single entry larger than the limit is evicted`() = runTest {
        writer(settings(maxBytes = 16)).save("x".repeat(64), clock.nowMillis(), null)

        assertTrue(dao.entities.isEmpty(), "запись больше лимита не помещается ни при каких условиях")
    }

    @Test
    fun `entry exactly at the limit is kept`() = runTest {
        writer(settings(maxBytes = 16)).save("x".repeat(16), clock.nowMillis(), null)

        assertEquals(1, dao.entities.size)
    }

    @Test
    fun `more pinned entries than the limit keeps them all`() = runTest {
        dao.upsert(entity("p1", "one", pinned = true))
        dao.upsert(entity("p2", "two", pinned = true))
        dao.upsert(entity("p3", "three", pinned = true))

        writer(settings(maxEntries = 1)).save("four", clock.nowMillis(), null)

        assertEquals(
            setOf("p1", "p2", "p3"),
            dao.entities.map { it.id }.toSet(),
            "закреплённые не вытесняются, даже если лимит меньше их числа; " +
                "новая незакреплённая запись вытесняется сразу",
        )
    }

    @Test
    fun `pinned entry older than ttl is kept`() = runTest {
        val stale = entity("pinned-stale", "old", pinned = true, timestamp = 0L)
        dao.upsert(stale)

        writer(settings()).save("fresh", clock.nowMillis(), null)

        assertTrue(dao.entities.any { it.id == "pinned-stale" })
    }

    private fun entity(
        id: String,
        text: String,
        pinned: Boolean,
        timestamp: Long = clock.nowMillis(),
    ) = ClipboardEventEntity(
        id = id,
        text = text,
        timestampMillis = timestamp,
        sourceDeviceId = null,
        isPinned = pinned,
    )
}
