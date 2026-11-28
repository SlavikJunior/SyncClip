package io.github.slavikjunior.syncclip.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

internal object AndroidClipboardDatabaseFactory : ClipboardDatabaseFactory {

    override fun create(name: String, context: Any?): AppDatabase {
        val appContext = requireNotNull(context as? Context) {
            "Android context is required to open the clipboard database"
        }
        return Room.databaseBuilder<AppDatabase>(
            context = appContext.applicationContext,
            name = name,
            factory = AppDatabaseConstructor::initialize,
        )
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*ClipboardMigrations.ALL)
            .build()
    }
}

actual fun clipboardDatabaseFactory(): ClipboardDatabaseFactory = AndroidClipboardDatabaseFactory
