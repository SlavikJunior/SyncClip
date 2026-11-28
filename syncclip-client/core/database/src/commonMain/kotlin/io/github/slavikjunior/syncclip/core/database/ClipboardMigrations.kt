package io.github.slavikjunior.syncclip.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Миграции схемы истории.
 *
 * Версия 2 добавила избранное (US-017) и срок автоудаления чувствительной
 * записи (US-021). Обе колонки имеют значения по умолчанию, поэтому
 * существующие записи остаются валидными: `isFavorite = 0`, `expiresAtMillis`
 * = `NULL` (бессрочно).
 */
internal object ClipboardMigrations {

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE clipboard_events ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0",
            )
            connection.execSQL(
                "ALTER TABLE clipboard_events ADD COLUMN expiresAtMillis INTEGER",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
