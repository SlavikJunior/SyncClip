package io.github.slavikjunior.syncclip.feature.sync.impl.domain.repository

import io.github.slavikjunior.syncclip.feature.sync.impl.domain.model.SyncPacket
import kotlinx.coroutines.flow.Flow

/**
 * Принятые локальные копирования — для статуса синхронизации.
 *
 * Поток горячий: подписчик видит только события, случившиеся при его жизни.
 */
internal interface ClipboardCaptureFeed {

    fun captures(): Flow<SyncPacket>
}
