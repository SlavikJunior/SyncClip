package io.github.slavikjunior.syncclip.server.app

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ops-endpoint'ы (CG-21) и тело 404 вне `/v1`. */
class HealthApiTest {

    private lateinit var database: io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory

    @BeforeEach
    fun setUp() {
        IntegrationSupport.assumeDatabase()
        database = IntegrationSupport.database()
    }

    @AfterEach
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun `live and ready report up with migrated database`() = testApplication {
        application { module(IntegrationSupport.config(), database) }

        val live = client.get("/health/live")
        assertEquals(HttpStatusCode.OK, live.status)
        assertTrue(live.bodyAsText().contains("\"up\""))

        val ready = client.get("/health/ready")
        assertEquals(HttpStatusCode.OK, ready.status)
        assertTrue(ready.bodyAsText().contains("\"up\""))
    }

    @Test
    fun `unknown path returns catalog error body`() = testApplication {
        application { module(IntegrationSupport.config(), database) }

        val response = client.get("/v1/unknown")
        assertEquals(HttpStatusCode.NotFound, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("not_found"), body)
        assertTrue(body.contains("\"message\""), body)
    }
}
