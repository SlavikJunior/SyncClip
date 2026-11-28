package io.github.slavikjunior.syncclip.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/**
 * Локальная база истории и отложенных правок устройств.
 *
 * Версия 2: избранное и `expiresAtMillis` для автоочистки чувствительных
 * записей. Миграция — [ClipboardMigrations.MIGRATION_1_2].
 */
@Database(
    entities = [ClipboardEventEntity::class, PendingDeviceUpdateEntity::class],
    version = 2,
    exportSchema = false,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clipboardEventDao(): ClipboardEventDao
    abstract fun pendingDeviceUpdateDao(): PendingDeviceUpdateDao
}

/** Реализация создаётся KSP для каждого target'а. */
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
