package io.github.slavikjunior.syncclip.server.app.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

/**
 * Единый Json-профиль (§6.5): неизвестные поля игнорируются, null'ы не
 * сериализуются, дефолты не пишутся — аддитивная совместимость `/v1`.
 */
val ServerJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
    prettyPrint = false
}

fun Application.configureSerialization(json: Json = ServerJson) {
    install(ContentNegotiation) {
        json(json)
    }
}
