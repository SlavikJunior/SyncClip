package io.github.slavikjunior.syncclip.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

internal object IosClipboardDatabaseFactory : ClipboardDatabaseFactory {

    override fun create(name: String, context: Any?): AppDatabase {
        val documents = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true,
        ).firstOrNull() as? String ?: error("Documents directory is unavailable")

        return Room.databaseBuilder<AppDatabase>(
            name = "$documents/$name",
            factory = AppDatabaseConstructor::initialize,
        )
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*ClipboardMigrations.ALL)
            .build()
    }
}

actual fun clipboardDatabaseFactory(): ClipboardDatabaseFactory = IosClipboardDatabaseFactory
