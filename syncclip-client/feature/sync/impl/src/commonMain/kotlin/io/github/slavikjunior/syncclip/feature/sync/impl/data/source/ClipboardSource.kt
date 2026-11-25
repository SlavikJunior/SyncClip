package io.github.slavikjunior.syncclip.feature.sync.impl.data.source

import io.github.slavikjunior.syncclip.shared.model.ClipboardPayload
import kotlinx.coroutines.flow.Flow

internal interface ClipboardSource {
    fun observe(): Flow<ClipboardPayload>
}
