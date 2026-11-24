package io.github.slavikjunior.syncclip.server.app.health

import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val checks: Map<String, String>? = null,
)

/**
 * Ops-endpoint'ы вне `/v1` и вне JWT (CG-21). `live` не трогает БД, иначе
 * недоступная БД приводит к перезапуску здорового процесса (§13.4).
 *
 * `ready` не выполняет DDL и не ходит в БД за списком миграций: статус
 * кэшируется [DatabaseFactory] (PERF-04). JDBC-запрос уводится с event loop.
 */
fun Route.healthRoutes(databaseFactory: DatabaseFactory) {
    get("/health/live") {
        call.respond(HealthResponse(status = "up"))
    }

    get("/health/ready") {
        val (dbOk, migrated) = withContext(Dispatchers.IO) {
            val ready = databaseFactory.isReady()
            ready to (ready && databaseFactory.migrationsUpToDate())
        }
        if (dbOk && migrated) {
            call.respond(HealthResponse(status = "up"))
        } else {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                HealthResponse(
                    status = "down",
                    checks = mapOf(
                        "db" to if (dbOk) "up" else "down",
                        "migrations" to if (migrated) "up" else "down",
                    ),
                ),
            )
        }
    }
}
