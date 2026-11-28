package io.github.slavikjunior.syncclip.core.archive

import io.github.slavikjunior.syncclip.core.database.ClipboardEventDao
import io.github.slavikjunior.syncclip.core.database.ClipboardEventEntity
import io.github.slavikjunior.syncclip.shared.model.Device
import io.github.slavikjunior.syncclip.shared.model.DeviceDirectory
import io.github.slavikjunior.syncclip.shared.model.DeviceIcon
import io.github.slavikjunior.syncclip.shared.model.DevicePlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomHistoryArchiveTest {

    private val dao = InMemoryClipboardEventDao(
        initial = listOf(
            entity(id = "1", text = "old", timestamp = 1_000, deviceId = "phone"),
            entity(id = "2", text = "new", timestamp = 2_000, deviceId = null),
        ),
    )

    private val directory = object : DeviceDirectory {
        override suspend fun devices(): List<Device> = listOf(
            Device(
                id = "phone",
                name = "Pixel 9",
                icon = DeviceIcon.PHONE,
                platform = DevicePlatform.ANDROID,
            ),
        )
    }

    @Test
    fun `export uses device names and respects the limit`() = runTest {
        val store = createHistoryArchiveStore(dao, directory)

        val exported = store.export(limit = 1)

        assertEquals(1, exported.size)
        // Лимит отдаёт самые свежие записи.
        assertEquals("new", exported.single().text)
        assertEquals(null, exported.single().deviceName)

        val full = store.export(limit = 10)
        assertEquals("Pixel 9", full.last().deviceName)
    }

    @Test
    fun `import skips duplicates by id and keeps existing rows`() = runTest {
        val store = createHistoryArchiveStore(dao, directory)
        val incoming = listOf(
            HistoryArchiveEntry("1", "old", 1_000, null, false, false),
            HistoryArchiveEntry("3", "imported", 3_000, null, true, true),
        )

        val result = store.import(incoming)

        assertEquals(1, result.imported)
        assertEquals(1, result.skipped)
        assertEquals("old", dao.findById("1")?.text)
        assertEquals(true, dao.findById("3")?.isFavorite)
        assertTrue(dao.findById("3")?.isPinned == true)
    }
}

private fun entity(
    id: String,
    text: String,
    timestamp: Long,
    deviceId: String?,
): ClipboardEventEntity = ClipboardEventEntity(
    id = id,
    text = text,
    timestampMillis = timestamp,
    sourceDeviceId = deviceId,
    isPinned = false,
)

/** DAO в памяти: архиву нужны только чтение последних строк и upsert. */
private class InMemoryClipboardEventDao(
    initial: List<ClipboardEventEntity>,
) : ClipboardEventDao {

    private val rows: MutableList<ClipboardEventEntity> = initial.toMutableList()

    override fun observePage(limit: Int, offset: Int): Flow<List<ClipboardEventEntity>> =
        flowOf(
            rows.sortedWith(
                compareByDescending<ClipboardEventEntity> { it.isPinned }
                    .thenByDescending { it.timestampMillis },
            ).drop(offset).take(limit),
        )

    override fun observeSearchPage(
        query: String,
        limit: Int,
        offset: Int,
    ): Flow<List<ClipboardEventEntity>> = flowOf(rows.drop(offset).take(limit))

    override suspend fun count(query: String): Int = rows.size

    override suspend fun newestRows(limit: Int): List<ClipboardEventEntity> =
        rows.sortedByDescending { it.timestampMillis }.take(limit)

    override suspend fun totalBytes(): Long = rows.sumOf { it.text.length.toLong() }

    override suspend fun findById(id: String): ClipboardEventEntity? =
        rows.firstOrNull { it.id == id }

    override suspend fun upsert(entity: ClipboardEventEntity) {
        rows.removeAll { it.id == entity.id }
        rows += entity
    }

    override suspend fun setPinned(id: String, pinned: Boolean) {
        val index = rows.indexOfFirst { it.id == id }
        if (index >= 0) rows[index] = rows[index].copy(isPinned = pinned)
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        val index = rows.indexOfFirst { it.id == id }
        if (index >= 0) rows[index] = rows[index].copy(isFavorite = favorite)
    }

    override suspend fun delete(id: String) {
        rows.removeAll { it.id == id }
    }

    override suspend fun oldestUnpinnedIds(limit: Int): List<String> =
        rows.filterNot { it.isPinned }.sortedBy { it.timestampMillis }.take(limit).map { it.id }

    override suspend fun deleteByIds(ids: List<String>) {
        rows.removeAll { it.id in ids }
    }

    override suspend fun expiredUnpinnedIds(cutoffMillis: Long): List<String> =
        rows.filterNot { it.isPinned }
            .filter { it.timestampMillis < cutoffMillis }
            .map { it.id }

    override suspend fun expiredSensitiveIds(nowMillis: Long): List<String> =
        rows.filterNot { it.isPinned }
            .filter { (it.expiresAtMillis ?: Long.MAX_VALUE) <= nowMillis }
            .map { it.id }
}
