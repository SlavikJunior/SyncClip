package io.github.slavikjunior.syncclip.core.archive

import io.github.slavikjunior.syncclip.core.database.ClipboardEventDao
import io.github.slavikjunior.syncclip.shared.model.DeviceDirectory

/**
 * Публичная фабрика архива: реализация остаётся `internal` (INV-26), а DI
 * собирает её из `:core:di`, не видя класса.
 */
public fun createHistoryArchiveStore(
    dao: ClipboardEventDao,
    deviceDirectory: DeviceDirectory? = null,
): HistoryArchiveStore = RoomHistoryArchive(dao, deviceDirectory)
