package io.github.slavikjunior.syncclip.core.archive

import io.github.slavikjunior.syncclip.core.database.ClipboardEventDao
import io.github.slavikjunior.syncclip.core.database.ClipboardEventEntity
import io.github.slavikjunior.syncclip.shared.model.DeviceDirectory

/**
 * Архив истории поверх Room.
 *
 * Имена устройств подтягиваются из каталога: в файле должно быть человекочитаемое
 * имя, а не `deviceId`, иначе архив нельзя прочитать после переустановки.
 */
internal class RoomHistoryArchive(
    private val dao: ClipboardEventDao,
    private val deviceDirectory: DeviceDirectory? = null,
) : HistoryArchiveStore {

    override suspend fun count(): Int = dao.count("")

    override suspend fun export(limit: Int): List<HistoryArchiveEntry> {
        val names = deviceDirectory
            ?.let { directory -> runCatching { directory.devices() }.getOrDefault(emptyList()) }
            ?.associate { device -> device.id to device.name }
            .orEmpty()
        return dao.newestRows(limit).map { entity -> entity.toArchive(names[entity.sourceDeviceId]) }
    }

    override suspend fun import(entries: List<HistoryArchiveEntry>): ArchiveImportResult {
        var imported = 0
        var skipped = 0
        entries.forEach { entry ->
            // Дубль по id: повторный импорт того же файла не создаёт копий.
            if (dao.findById(entry.id) != null) {
                skipped++
                return@forEach
            }
            dao.upsert(entry.toEntity())
            imported++
        }
        return ArchiveImportResult(imported = imported, skipped = skipped)
    }

    private fun ClipboardEventEntity.toArchive(deviceName: String?): HistoryArchiveEntry =
        HistoryArchiveEntry(
            id = id,
            text = text,
            timestampMillis = timestampMillis,
            deviceName = deviceName,
            isPinned = isPinned,
            isFavorite = isFavorite,
        )

    private fun HistoryArchiveEntry.toEntity(): ClipboardEventEntity = ClipboardEventEntity(
        id = id,
        text = text,
        timestampMillis = timestampMillis,
        // Устройство из чужого архива может быть не зарегистрировано: имя
        // сохранить некуда, поэтому источник остаётся пустым.
        sourceDeviceId = null,
        isPinned = isPinned,
        isFavorite = isFavorite,
        expiresAtMillis = null,
    )
}
