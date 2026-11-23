package io.github.slavikjunior.syncclip.feature.sync.impl.data.source

import io.github.slavikjunior.syncclip.shared.model.ClipboardPayload
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Платформенные слушатели буфера (Android/iOS/Desktop) публикуют события сюда.
 * SharedFlow с DROP_OLDEST не блокирует системный callback.
 */
internal class InMemoryClipboardSource : ClipboardSource {

    private val events = MutableSharedFlow<ClipboardPayload>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun observe(): Flow<ClipboardPayload> = events

    fun publish(payload: ClipboardPayload) {
        events.tryEmit(payload)
    }
}
