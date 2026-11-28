package io.github.slavikjunior.syncclip.feature.sync.impl

import io.github.slavikjunior.syncclip.core.datastore.AppSettings
import io.github.slavikjunior.syncclip.core.datastore.ClipboardSettings
import io.github.slavikjunior.syncclip.core.database.ClipboardEventEntity
import io.github.slavikjunior.syncclip.feature.sync.impl.data.repository.RoomClipboardHistoryWriter
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SensitiveContentDetector
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** US-021: срок жизни чувствительной записи и её автоудаление. */
internal class SensitiveHistoryWriterTest {

    private val dao = FakeClipboardEventDao()
    private val clock = TestClock()
    private val appSettings = FakeAppSettingsStore()
    private var ids = 0

    private fun writer() = RoomClipboardHistoryWriter(
        dao = dao,
        clock = clock,
        settings = FakeClipboardSettingsStore(
            ClipboardSettings(maxEntries = 200, ttlHours = 7 * 24, maxBytes = 10L * 1024 * 1024),
        ),
        appSettings = appSettings,
        newId = { "id-${ids++}" },
    )

    @Test
    fun `sensitive text gets a five minute deadline when enabled`() = runTest {
        appSettings.update { it.copy(autoClearSensitive = true) }

        writer().save("p@ssw0rd123!", clock.nowMillis(), sourceDeviceId = null)

        assertEquals(
            clock.nowMillis() + SensitiveContentDetector.SENSITIVE_TTL_MILLIS,
            dao.entities.single().expiresAtMillis,
        )
    }

    @Test
    fun `ordinary text is not marked sensitive`() = runTest {
        appSettings.update { it.copy(autoClearSensitive = true) }

        writer().save("Hello, World!", clock.nowMillis(), sourceDeviceId = null)

        assertNull(dao.entities.single().expiresAtMillis)
    }

    @Test
    fun `disabled setting stores passwords without a deadline`() = runTest {
        writer().save("p@ssw0rd123!", clock.nowMillis(), sourceDeviceId = null)

        assertNull(dao.entities.single().expiresAtMillis)
    }

    @Test
    fun `expired sensitive entry is deleted on the next save`() = runTest {
        dao.upsert(
            ClipboardEventEntity(
                id = "old-secret",
                text = "p@ssw0rd123!",
                timestampMillis = clock.nowMillis(),
                sourceDeviceId = null,
                isPinned = false,
                expiresAtMillis = clock.nowMillis() - 1,
            ),
        )

        writer().save("next", clock.nowMillis(), sourceDeviceId = null)

        assertEquals(listOf("next"), dao.entities.map { it.text })
    }

    @Test
    fun `pinned sensitive entry survives its deadline`() = runTest {
        dao.upsert(
            ClipboardEventEntity(
                id = "pinned-secret",
                text = "p@ssw0rd123!",
                timestampMillis = clock.nowMillis(),
                sourceDeviceId = null,
                isPinned = true,
                expiresAtMillis = clock.nowMillis() - 1,
            ),
        )

        writer().save("next", clock.nowMillis(), sourceDeviceId = null)

        assertEquals(
            listOf("pinned-secret", "id-0").sorted(),
            dao.entities.map { it.id }.sorted(),
        )
    }
    @Test
    fun `auto cleanup is off by default`() {
        assertEquals(false, AppSettings().autoClearSensitive)
    }
}
