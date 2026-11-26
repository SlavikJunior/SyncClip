package io.github.slavikjunior.syncclip.core.clipboard

/**
 * Android не нуждается в очереди: Share Activity передаёт текст в конвейер
 * сразу, поэтому очередь всегда пуста.
 */
public actual fun createPendingShareSource(platformContext: Any?): PendingShareSource =
    EmptyPendingShareSource
