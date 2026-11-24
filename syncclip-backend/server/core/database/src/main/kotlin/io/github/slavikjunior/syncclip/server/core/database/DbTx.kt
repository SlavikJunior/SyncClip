package io.github.slavikjunior.syncclip.server.core.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.inTopLevelSuspendTransaction

/**
 * Транзакция use case'а (дизайн §4.4): одна tx на сервисный вызов.
 *
 * JDBC-работа выполняется на Dispatchers.IO, транзакция — top-level
 * (`inTopLevelSuspendTransaction`), чтобы вызов из WS-хендлера не превращался
 * в вложенную транзакцию над уже завершённой внешней.
 */
suspend fun <T> tx(db: Database, block: suspend JdbcTransaction.() -> T): T =
    withContext(Dispatchers.IO) {
        inTopLevelSuspendTransaction(db = db) { block() }
    }
