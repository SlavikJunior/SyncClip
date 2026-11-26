package io.github.slavikjunior.syncclip.core.clipboard

/** Платформа без очереди Share Extension (Android отправляет сразу, Desktop — нет). */
internal object EmptyPendingShareSource : PendingShareSource {

    override fun take(): List<String> = emptyList()
}
