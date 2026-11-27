package io.github.slavikjunior.syncclip.feature.sync.impl

import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.IncomingClipboardAction
import io.github.slavikjunior.syncclip.feature.sync.impl.domain.usecase.ResolveIncomingClipboardUseCase
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ResolveIncomingClipboardUseCaseTest {

    private val useCase = ResolveIncomingClipboardUseCase()
    private val lastLocalCopy = 10_000L

    @Test
    fun `remote packet is inserted when window passed`() {
        val action = useCase(
            isOnline = true,
            autoInsert = true,
            nowMillis = lastLocalCopy + 501,
            lastLocalCaptureMillis = lastLocalCopy,
        )

        assertEquals(IncomingClipboardAction.SAVE_AND_INSERT, action)
    }

    @Test
    fun `packet within 500 ms of local copy is only saved`() {
        val action = useCase(
            isOnline = true,
            autoInsert = true,
            nowMillis = lastLocalCopy + 499,
            lastLocalCaptureMillis = lastLocalCopy,
        )

        assertEquals(IncomingClipboardAction.SAVE_ONLY, action, "нельзя перезаписывать свежее копирование")
    }

    @Test
    fun `auto insert off means save only`() {
        val action = useCase(
            isOnline = true,
            autoInsert = false,
            nowMillis = lastLocalCopy + 10_000,
            lastLocalCaptureMillis = lastLocalCopy,
        )

        assertEquals(IncomingClipboardAction.SAVE_ONLY, action)
    }

    @Test
    fun `offline means save only`() {
        val action = useCase(
            isOnline = false,
            autoInsert = true,
            nowMillis = lastLocalCopy + 10_000,
            lastLocalCaptureMillis = lastLocalCopy,
        )

        assertEquals(IncomingClipboardAction.SAVE_ONLY, action)
    }
}
