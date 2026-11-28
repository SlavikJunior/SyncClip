package io.github.slavikjunior.syncclip.core.archive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryArchiveCodecTest {

    private val entries = listOf(
        HistoryArchiveEntry(
            id = "uuid-1",
            text = "Hello, World!",
            timestampMillis = 1_732_112_266_000,
            deviceName = "MacBook Pro",
            isPinned = false,
            isFavorite = true,
        ),
        HistoryArchiveEntry(
            id = "uuid-2",
            text = "val state = _state.asStateFlow()",
            timestampMillis = 1_732_112_000_000,
            deviceName = null,
            isPinned = true,
            isFavorite = false,
        ),
    )

    @Test
    fun `json round trip keeps every field`() {
        val encoded = HistoryArchiveCodec.encode(
            HistoryArchiveFormat.JSON,
            entries,
            exportedAtIso = "2026-11-20T15:30:00Z",
        )
        assertTrue(encoded.contains("\"version\": \"1.0\""))
        assertTrue(encoded.contains("\"exported_at\": \"2026-11-20T15:30:00Z\""))

        val decoded = HistoryArchiveCodec.decode(HistoryArchiveFormat.JSON, encoded).getOrThrow()
        assertEquals(entries, decoded)
    }

    @Test
    fun `csv round trip escapes commas quotes and line breaks`() {
        val tricky = entries + HistoryArchiveEntry(
            id = "uuid-3",
            text = "line1\nline2 \"quoted\", with comma",
            timestampMillis = 1_732_000_000_000,
            deviceName = "Dev, \"lab\"",
            isPinned = false,
            isFavorite = false,
        )
        val encoded = HistoryArchiveCodec.encode(
            HistoryArchiveFormat.CSV,
            tricky,
            exportedAtIso = "2026-11-20T15:30:00Z",
        )
        assertTrue(encoded.startsWith("id,text,timestamp,device_name,is_pinned,is_favorite"))

        val decoded = HistoryArchiveCodec.decode(HistoryArchiveFormat.CSV, encoded).getOrThrow()
        assertEquals(tricky, decoded)
    }

    @Test
    fun `wrong json version is rejected`() {
        val encoded = HistoryArchiveCodec.encode(
            HistoryArchiveFormat.JSON,
            entries,
            exportedAtIso = "2026-11-20T15:30:00Z",
        ).replace("\"1.0\"", "\"9.9\"")

        val result = HistoryArchiveCodec.decode(HistoryArchiveFormat.JSON, encoded)

        assertTrue(result.isFailure)
        assertEquals(
            ArchiveError.INVALID_FORMAT,
            (result.exceptionOrNull() as HistoryArchiveCodec.ArchiveFormatException).error,
        )
    }

    @Test
    fun `csv with foreign header is rejected`() {
        val result = HistoryArchiveCodec.decode(
            HistoryArchiveFormat.CSV,
            "id,text,timestamp\n1,hello,123",
        )
        assertTrue(result.isFailure)
        assertEquals(
            ArchiveError.INVALID_FORMAT,
            (result.exceptionOrNull() as HistoryArchiveCodec.ArchiveFormatException).error,
        )
    }

    @Test
    fun `empty archive is reported as empty not invalid`() {
        val emptyJson = HistoryArchiveCodec.encode(
            HistoryArchiveFormat.JSON,
            emptyList(),
            exportedAtIso = "2026-11-20T15:30:00Z",
        )
        val result = HistoryArchiveCodec.decode(HistoryArchiveFormat.JSON, emptyJson)

        assertTrue(result.isFailure)
        assertEquals(
            ArchiveError.EMPTY,
            (result.exceptionOrNull() as HistoryArchiveCodec.ArchiveFormatException).error,
        )
    }

    @Test
    fun `garbage is not a crash`() {
        assertTrue(HistoryArchiveCodec.decode(HistoryArchiveFormat.JSON, "{{{").isFailure)
        assertTrue(HistoryArchiveCodec.decode(HistoryArchiveFormat.CSV, "").isFailure)
        assertTrue(
            HistoryArchiveCodec.decode(HistoryArchiveFormat.CSV, "id,text,timestamp,device_name,is_pinned,is_favorite\n1,x,not-a-number,,false,false")
                .isFailure,
        )
    }

    @Test
    fun `archive larger than the export limit survives a round trip`() {
        val big = (1..1_200).map { index ->
            HistoryArchiveEntry(
                id = "id-$index",
                text = "clipboard entry #$index с юникодом",
                timestampMillis = 1_700_000_000_000 + index,
                deviceName = "Device ${index % 3}",
                isPinned = index % 10 == 0,
                isFavorite = index % 7 == 0,
            )
        }
        listOf(HistoryArchiveFormat.JSON, HistoryArchiveFormat.CSV).forEach { format ->
            val encoded = HistoryArchiveCodec.encode(format, big, "2026-11-20T15:30:00Z")
            val decoded = HistoryArchiveCodec.decode(format, encoded).getOrThrow()
            assertEquals(big, decoded)
        }
    }
}
