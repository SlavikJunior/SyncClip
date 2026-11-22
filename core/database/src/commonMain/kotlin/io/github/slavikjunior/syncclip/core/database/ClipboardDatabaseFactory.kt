package io.github.slavikjunior.syncclip.core.database

/**
 * Фабрика БД без зависимостей от платформенных API в общем коде.
 * `context` используется только на Android и игнорируется на остальных таргетах.
 */
interface ClipboardDatabaseFactory {
    fun create(name: String, context: Any?): AppDatabase
}

/** Платформенная реализация подключается в точке входа. */
expect fun clipboardDatabaseFactory(): ClipboardDatabaseFactory
