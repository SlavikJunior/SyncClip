package io.github.slavikjunior.syncclip.feature.sync.impl

import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.LocalCaptureVerdict
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.AcceptLocalCaptureUseCase
import kotlin.test.Test
import kotlin.test.assertEquals

internal class AcceptLocalCaptureUseCaseTest {

    private val useCase = AcceptLocalCaptureUseCase()

    @Test
    fun `blank text is rejected`() {
        assertEquals(LocalCaptureVerdict.EMPTY, useCase(""))
        assertEquals(LocalCaptureVerdict.EMPTY, useCase("   \n"))
    }

    @Test
    fun `text of exactly the limit is accepted`() {
        val limit = "a".repeat(512 * 1024)

        assertEquals(LocalCaptureVerdict.ACCEPT, useCase(limit))
    }

    @Test
    fun `text above the limit is rejected`() {
        val oversized = "a".repeat(512 * 1024 + 1)

        assertEquals(LocalCaptureVerdict.TOO_LARGE, useCase(oversized))
    }

    @Test
    fun `multibyte text is measured in utf8 bytes`() {
        // 300 000 кириллических символов — 600 000 байт UTF-8, больше лимита.
        val cyrillic = "я".repeat(300_000)

        assertEquals(LocalCaptureVerdict.TOO_LARGE, useCase(cyrillic))
    }
}
