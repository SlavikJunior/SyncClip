package io.github.slavikjunior.syncclip.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.nio.file.Files
import java.nio.file.Path

internal object JvmClipboardDatabaseFactory : ClipboardDatabaseFactory {

    override fun create(name: String, context: Any?): AppDatabase {
        val directory = Path.of(System.getProperty("user.home"), ".syncclip")
        Files.createDirectories(directory)

        return Room.databaseBuilder<AppDatabase>(
            name = directory.resolve(name).toAbsolutePath().toString(),
            factory = AppDatabaseConstructor::initialize,
        )
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*ClipboardMigrations.ALL)
            .build()
    }
}

actual fun clipboardDatabaseFactory(): ClipboardDatabaseFactory = JvmClipboardDatabaseFactory
