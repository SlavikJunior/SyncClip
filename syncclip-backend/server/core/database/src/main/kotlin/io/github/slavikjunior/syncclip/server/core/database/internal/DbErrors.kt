package io.github.slavikjunior.syncclip.server.core.database.internal

import java.sql.SQLException

/** Утилиты распознавания SQLSTATE: наружу из репозиториев исключения БД не выходят. */
internal object DbErrors {

    private const val UNIQUE_VIOLATION = "23505"
    private const val FOREIGN_KEY_VIOLATION = "23503"

    fun isUniqueViolation(error: Throwable): Boolean = hasSqlState(error, UNIQUE_VIOLATION)

    fun isForeignKeyViolation(error: Throwable): Boolean = hasSqlState(error, FOREIGN_KEY_VIOLATION)

    private fun hasSqlState(error: Throwable, state: String): Boolean =
        generateSequence(error) { it.cause }
            .filterIsInstance<SQLException>()
            .any { it.sqlState == state }
}
