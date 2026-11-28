package io.github.slavikjunior.syncclip.core.database

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Миграция 1 → 2 (US-017, US-021) на настоящем SQLite.
 *
 * Схема версии 1 создаётся вручную и помечается `user_version = 1`; Room
 * открывает файл с [ClipboardMigrations.MIGRATION_1_2] и должен добавить
 * `isFavorite`/`expiresAtMillis`, не потеряв существующих записей.
 */
class ClipboardMigrationTest {

    @Test
    fun `migration from version 1 keeps rows and adds new columns`() {
        val file = Files.createTempFile("syncclip-migration", ".db").toFile()
        file.deleteOnExit()

        val driver = BundledSQLiteDriver()
        driver.open(file.absolutePath).use { connection -> createVersionOneSchema(connection) }

        val database = Room.databaseBuilder<AppDatabase>(
            name = file.absolutePath,
            factory = AppDatabaseConstructor::initialize,
        )
            .setDriver(driver)
            .addMigrations(*ClipboardMigrations.ALL)
            .build()

        try {
            runBlocking {
                val dao = database.clipboardEventDao()
                val existing = dao.findById("legacy")
                assertEquals("old text", existing?.text)
                // Значения по умолчанию для новых колонок.
                assertEquals(false, existing?.isFavorite)
                assertNull(existing?.expiresAtMillis)

                dao.setFavorite("legacy", true)
                assertTrue(dao.findById("legacy")?.isFavorite == true)

                dao.upsert(
                    ClipboardEventEntity(
                        id = "sensitive",
                        text = "p@ssw0rd123!",
                        timestampMillis = 2_000,
                        sourceDeviceId = null,
                        isPinned = false,
                        expiresAtMillis = 5_000,
                    ),
                )
                assertEquals(listOf("sensitive"), dao.expiredSensitiveIds(nowMillis = 6_000))
            }
        } finally {
            database.close()
            file.delete()
        }
    }

    /** Схема ровно в том виде, в каком её создавал Room версии 1. */
    private fun createVersionOneSchema(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `clipboard_events` (
                `id` TEXT NOT NULL,
                `text` TEXT NOT NULL,
                `timestampMillis` INTEGER NOT NULL,
                `sourceDeviceId` TEXT,
                `isPinned` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_clipboard_events_text` " +
                "ON `clipboard_events` (`text`)",
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pending_device_updates` (
                `deviceId` TEXT NOT NULL,
                `name` TEXT,
                `icon` TEXT,
                PRIMARY KEY(`deviceId`)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "INSERT INTO `clipboard_events` " +
                "(`id`, `text`, `timestampMillis`, `sourceDeviceId`, `isPinned`) " +
                "VALUES ('legacy', 'old text', 1, NULL, 0)",
        )
        connection.execSQL("PRAGMA user_version = 1")
    }
}
