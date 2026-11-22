package io.github.slavikjunior.syncclip.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Отложенное переименование/смена иконки: US-001 требует применить
 * изменение при следующем подключении, если устройство было офлайн.
 */
@Entity(tableName = "pending_device_updates")
data class PendingDeviceUpdateEntity(
    @PrimaryKey val deviceId: String,
    val name: String?,
    val icon: String?,
)
