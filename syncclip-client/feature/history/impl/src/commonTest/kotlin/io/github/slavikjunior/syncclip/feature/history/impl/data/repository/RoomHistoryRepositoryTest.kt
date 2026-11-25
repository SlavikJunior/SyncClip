package io.github.slavikjunior.syncclip.feature.history.impl.data.repository

import io.github.slavikjunior.syncclip.core.cache.KacheWrapper
import io.github.slavikjunior.syncclip.core.database.ClipboardEventEntity
import io.github.slavikjunior.syncclip.feature.history.impl.data.cache.HistoryPageCache
import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

internal class RoomHistoryRepositoryTest {

    private val clock = MutableClock()
    private val dao = FakeClipboardEventDao(
        listOf(
            entity(id = "1", text = "first"),
            entity(id = "2", text = "second"),
        ),
    )
    private val repository = RoomHistoryRepository(
        dao = dao,
        pageCache = HistoryPageCache(
            KacheWrapper<String, List<HistoryEntry>>(
                maxSize = 2,
                defaultTtl = 1.minutes,
                clock = clock,
            ),
        ),
    )

    @Test
    fun `first page without search is cached`() = runTest {
        val page = repository.observePage(query = "", limit = 50, offset = 0).first()

        assertEquals(listOf("1", "2"), page.map { it.id })
        assertEquals(page, repository.cachedFirstPage(limit = 50))
    }

    @Test
    fun `search results are not cached`() = runTest {
        repository.observePage(query = "second", limit = 50, offset = 0).first()

        assertNull(repository.cachedFirstPage(limit = 50))
    }

    @Test
    fun `next pages are not cached`() = runTest {
        repository.observePage(query = "", limit = 50, offset = 1).first()

        assertNull(repository.cachedFirstPage(limit = 50))
    }

    @Test
    fun `cached page expires after ttl`() = runTest {
        repository.observePage(query = "", limit = 50, offset = 0).first()

        clock.advance(1.minutes)
        assertNotNull(repository.cachedFirstPage(limit = 50))

        clock.advance(1.milliseconds)
        assertNull(repository.cachedFirstPage(limit = 50))
    }

    @Test
    fun `mutation invalidates cached page`() = runTest {
        repository.observePage(query = "", limit = 50, offset = 0).first()
        assertNotNull(repository.cachedFirstPage(limit = 50))

        repository.delete("1")

        assertNull(repository.cachedFirstPage(limit = 50))
    }

    @Test
    fun `oversized page is not cached`() = runTest {
        dao.publish(listOf(entity(id = "big", text = "x".repeat(65_000))))

        repository.observePage(query = "", limit = 50, offset = 0).first()

        assertNull(repository.cachedFirstPage(limit = 50))
    }

    private fun entity(id: String, text: String): ClipboardEventEntity = ClipboardEventEntity(
        id = id,
        text = text,
        timestampMillis = 1_000L,
        sourceDeviceId = null,
        isPinned = false,
    )
}
