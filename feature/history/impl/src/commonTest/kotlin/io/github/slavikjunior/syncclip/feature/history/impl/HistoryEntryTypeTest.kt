package io.github.slavikjunior.syncclip.feature.history.impl

import io.github.slavikjunior.syncclip.feature.history.impl.domain.model.HistoryEntryType
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryEntryTypeTest {

    @Test
    fun `https url is a link`() {
        assertEquals(
            HistoryEntryType.LINK,
            HistoryEntryType.fromText("https://github.com/JetBrains/compose-multiplatform"),
        )
    }

    @Test
    fun `bare domain is a link`() {
        assertEquals(
            HistoryEntryType.LINK,
            HistoryEntryType.fromText("github.com/JetBrains/compose-multiplatform"),
        )
    }

    @Test
    fun `kotlin snippet is code`() {
        assertEquals(
            HistoryEntryType.CODE,
            HistoryEntryType.fromText("val state = _state.asStateFlow()"),
        )
    }

    @Test
    fun `plain sentence is text`() {
        assertEquals(
            HistoryEntryType.TEXT,
            HistoryEntryType.fromText("Buy oat milk and olive oil"),
        )
    }

    @Test
    fun `url inside a sentence stays text`() {
        assertEquals(
            HistoryEntryType.TEXT,
            HistoryEntryType.fromText("Look at github.com/JetBrains later"),
        )
    }
}
