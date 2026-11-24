package io.github.slavikjunior.syncclip.server.core.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.slavikjunior.syncclip.server.core.config.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * Пул соединений + Exposed-фасад. Схему создаёт [MigrationRunner],
 * а не SchemaUtils (канонический DDL — docs/backend/DB_SCHEMA.sql).
 */
class DatabaseFactory(private val config: DatabaseConfig) : AutoCloseable {

    val dataSource: HikariDataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            maximumPoolSize = config.maxPoolSize
            connectionTimeout = config.connectionTimeoutMs
            poolName = "syncclip-pool"
            isAutoCommit = true
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            // Медленный запрос не должен держать соединение бесконечно (LOW-14).
            connectionInitSql = "SET statement_timeout = ${config.statementTimeoutMs}"
            leakDetectionThreshold = 10_000
            validate()
        },
    )

    val database: Database = Database.connect(dataSource)

    @Volatile
    private var migrationChecked = false

    @Volatile
    private var migrationsUpToDate = false

    fun migrate() {
        MigrationRunner(dataSource).migrate()
        migrationChecked = true
        migrationsUpToDate = true
    }

    /**
     * Пересчитать статус миграций без DDL. Вызывается на старте и из
     * maintenance-job; health/ready читает только кэш (PERF-04).
     */
    fun refreshMigrationStatus(): Boolean {
        val ok = runCatching { MigrationRunner(dataSource).isUpToDate() }.getOrDefault(false)
        migrationsUpToDate = ok
        migrationChecked = true
        return ok
    }

    /** Последний известный статус; при первом обращении выполняет read-only проверку. */
    fun migrationsUpToDate(): Boolean =
        if (migrationChecked) migrationsUpToDate else refreshMigrationStatus()

    /** Проверка готовности: `SELECT 1` с коротким таймаутом (health/ready). */
    fun isReady(timeoutMillis: Long = 1_000): Boolean =
        try {
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.queryTimeout = (timeoutMillis / 1000).toInt().coerceAtLeast(1)
                    statement.executeQuery("SELECT 1").use { rs -> rs.next() }
                }
            }
        } catch (e: Exception) {
            false
        }

    override fun close() {
        dataSource.close()
    }
}
