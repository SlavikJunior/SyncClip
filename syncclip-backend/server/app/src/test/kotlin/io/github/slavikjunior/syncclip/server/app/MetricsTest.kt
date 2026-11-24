package io.github.slavikjunior.syncclip.server.app

import io.github.slavikjunior.syncclip.server.app.metrics.MetricsRegistry
import io.github.slavikjunior.syncclip.server.app.plugins.ServerJson
import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** OPS-07: Prometheus-метрики и защищённый `/metrics`. */
class MetricsTest {

    private lateinit var database: DatabaseFactory

    @BeforeEach
    fun setUp() {
        IntegrationSupport.assumeDatabase()
        database = IntegrationSupport.database()
        IntegrationSupport.truncate(database)
    }

    @AfterEach
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun `registry renders counters and summaries`() {
        val registry = MetricsRegistry()
        registry.addCounter("syncclip_demo_total", 2)
        registry.increment("syncclip_demo_total", mapOf("code" to "x"))
        registry.observe("syncclip_demo_seconds", 0.5)

        val output = registry.render()
        assertTrue(output.contains("# TYPE syncclip_demo_total counter"), output)
        assertTrue(output.contains("syncclip_demo_total 2"), output)
        assertTrue(output.contains("syncclip_demo_total{code=\"x\"} 1"), output)
        assertTrue(output.contains("syncclip_demo_seconds_count 1"), output)
        assertTrue(output.contains("syncclip_demo_seconds_sum 0.5"), output)
    }

    @Test
    fun `metrics endpoint requires token and exposes gauges`() = testApplication {
        application {
            module(
                IntegrationSupport.config(
                    mapOf(
                        "METRICS_ENABLED" to "true",
                        "METRICS_TOKEN" to "metrics-test-token-123456",
                    ),
                ),
                database,
            )
        }
        val client = jsonClient()

        assertEquals(HttpStatusCode.Unauthorized, client.get("/metrics").status)

        val authorized = client.get("/metrics") {
            header("X-Metrics-Token", "metrics-test-token-123456")
        }
        assertEquals(HttpStatusCode.OK, authorized.status)
        val body = authorized.bodyAsText()
        assertTrue(body.contains("syncclip_ws_active_connections 0"), body)
        assertTrue(body.contains("syncclip_offline_queue_depth 0"), body)
    }

    @Test
    fun `metrics endpoint is absent when disabled`() = testApplication {
        application { module(IntegrationSupport.config(), database) }
        assertEquals(HttpStatusCode.NotFound, jsonClient().get("/metrics").status)
        assertFalse(IntegrationSupport.config().observability.metricsEnabled)
    }

    private fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
        install(ContentNegotiation) { json(ServerJson) }
    }
}
