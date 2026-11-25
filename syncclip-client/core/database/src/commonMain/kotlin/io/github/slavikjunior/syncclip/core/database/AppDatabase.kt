package io.github.slavikjunior.syncclip.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [ClipboardEventEntity::class, PendingDeviceUpdateEntity::class],
    version = 1,
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
