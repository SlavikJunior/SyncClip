package io.github.slavikjunior.syncclip.server.core.database

import java.security.MessageDigest
import java.sql.Connection
import javax.sql.DataSource

/**
 * Минимальный идемпотентный мигратор схемы.
 *
 * Отклонение от §10.5: Flyway помечен «требует апрува», поэтому применениe
 * V1__init.sql выполняет собственный раннер с таблицей `schema_history`
 * (version + checksum). Поведение для MVP то же: файлы применяются по порядку,
 * изменённый checksum — ошибка, откат — только restore из бэкапа.
 *
 * Многoинстансный старт сериализуется advisory lock'ом (OPS-04), а проверка
 * готовности не выполняет DDL (PERF-04).
 */
class MigrationRunner(private val dataSource: DataSource) {

    fun migrate() {
        dataSource.connection.use { connection ->
            acquireLock(connection)
            try {
                ensureHistoryTable(connection)
                val applied = readApplied(connection)
                val pending = loadMigrations().sortedBy { it.version }
                // Миграции могут быть длиннее обычного statement_timeout; после
                // завершения возвращаем прежнее значение, иначе соединение
                // вернётся в пул без защиты (LOW-14).
                val previousTimeout = statementTimeout(connection)
                connection.createStatement().use { it.execute("SET statement_timeout = 0") }
                try {
                    for (migration in pending) {
                        val known = applied[migration.version]
                        when {
                            known == migration.checksum -> continue
                            known != null -> error(
                                "Migration ${migration.version} checksum mismatch: applied=$known actual=${migration.checksum}",
                            )
                        }
                        apply(connection, migration)
                    }
                } finally {
                    connection.createStatement().use {
                        it.execute("SET statement_timeout = '$previousTimeout'")
                    }
                }
            } finally {
                releaseLock(connection)
            }
        }
    }

    /** Read-only сверка истории с файлами миграций (health/ready, старт). */
    fun isUpToDate(): Boolean =
        dataSource.connection.use { connection ->
            if (!historyTableExists(connection)) return@use false
            val applied = readApplied(connection)
            val expected = loadMigrations()
            expected.isNotEmpty() && expected.all { applied[it.version] == it.checksum }
        }

    private fun acquireLock(connection: Connection) {
        connection.prepareStatement("SELECT pg_advisory_lock(?)").use { statement ->
            statement.setLong(1, MIGRATION_LOCK_KEY)
            statement.execute()
        }
    }

    private fun releaseLock(connection: Connection) {
        runCatching {
            connection.prepareStatement("SELECT pg_advisory_unlock(?)").use { statement ->
                statement.setLong(1, MIGRATION_LOCK_KEY)
                statement.execute()
            }
        }
    }

    private fun historyTableExists(connection: Connection): Boolean =
        connection.prepareStatement("SELECT to_regclass('public.schema_history') IS NOT NULL").use { statement ->
            statement.executeQuery().use { rs -> rs.next() && rs.getBoolean(1) }
        }

    private fun statementTimeout(connection: Connection): String =
        connection.createStatement().use { statement ->
            statement.executeQuery("SHOW statement_timeout").use { rs ->
                if (rs.next()) rs.getString(1) else "0"
            }
        }

    private fun ensureHistoryTable(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS schema_history (
                    version    VARCHAR(64)  PRIMARY KEY,
                    checksum   VARCHAR(64)  NOT NULL,
                    applied_at TIMESTAMPTZ  NOT NULL DEFAULT now()
                )
                """.trimIndent(),
            )
        }
    }

    private fun readApplied(connection: Connection): Map<String, String> {
        val result = mutableMapOf<String, String>()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT version, checksum FROM schema_history").use { rs ->
                while (rs.next()) result[rs.getString(1)] = rs.getString(2)
            }
        }
        return result
    }

    private fun apply(connection: Connection, migration: Migration) {
        connection.autoCommit = false
        try {
            connection.createStatement().use { statement ->
                SqlScript.split(migration.sql)
                    .filterNot(SqlScript::isTransactionControl)
                    .forEach { statement.execute(it) }
            }
            connection.prepareStatement(
                "INSERT INTO schema_history (version, checksum) VALUES (?, ?)",
            ).use { statement ->
                statement.setString(1, migration.version)
                statement.setString(2, migration.checksum)
                statement.executeUpdate()
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw IllegalStateException("Migration ${migration.version} failed", e)
        } finally {
            connection.autoCommit = true
        }
    }

    private fun loadMigrations(): List<Migration> {
        val index = readResource("db/migration/index.txt") ?: return emptyList()
        return index.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { file ->
                val sql = readResource("db/migration/$file")
                    ?: error("Migration file not found on classpath: db/migration/$file")
                Migration(
                    version = file.substringBefore("__"),
                    sql = sql,
                    checksum = sha256(sql),
                )
            }
            .toList()
    }

    private fun readResource(path: String): String? =
        javaClass.classLoader.getResourceAsStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private data class Migration(val version: String, val sql: String, val checksum: String)

    private companion object {
        /** Произвольный стабильный ключ advisory lock'а миграций. */
        const val MIGRATION_LOCK_KEY = 7_250_001_001L
    }
}
