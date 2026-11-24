package io.github.slavikjunior.syncclip.server.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** OPS-01: опечатка в env не должна молча превращаться в дефолт. */
class ConfigLoaderTest {

    private fun env(vararg overrides: Pair<String, String>): Map<String, String> =
        mapOf(
            "DB_URL" to "jdbc:postgresql://localhost:5432/syncclip_test",
            "DB_USER" to "postgres",
            "DB_PASSWORD" to "test-password",
            "JWT_SECRET" to "unit-test-secret-unit-test-secret-123",
        ) + overrides.toMap()

    private fun loadFails(vararg overrides: Pair<String, String>): String =
        assertFailsWith<ConfigValidationException> { ConfigLoader.load(env(*overrides)) }.message.orEmpty()

    @Test
    fun `valid dev config loads with defaults`() {
        val config = ConfigLoader.load(env())
        assertEquals(AppEnvironment.DEV, config.environment)
        assertEquals(8080, config.server.port)
        assertEquals(600_000, config.security.password.iterations)
        assertTrue(config.http.trustedProxies.isEmpty())
    }

    @Test
    fun `non numeric port fails fast`() {
        assertTrue(loadFails("SERVER_PORT" to "not-a-number").contains("SERVER_PORT"))
    }

    @Test
    fun `invalid boolean fails fast`() {
        assertTrue(loadFails("LOG_JSON" to "maybe").contains("LOG_JSON"))
    }

    @Test
    fun `zero or negative rate limit is rejected`() {
        assertTrue(loadFails("RATE_LIMIT_LOGIN_PER_MINUTE" to "-5").contains("RATE_LIMIT_LOGIN_PER_MINUTE"))
        assertTrue(loadFails("RATE_LIMIT_DEFAULT_PER_MINUTE" to "0").contains("RATE_LIMIT_DEFAULT_PER_MINUTE"))
    }

    @Test
    fun `zero cleanup interval is rejected`() {
        assertTrue(loadFails("QUEUE_CLEANUP_INTERVAL_SECONDS" to "0").contains("QUEUE_CLEANUP_INTERVAL_SECONDS"))
    }

    @Test
    fun `unknown log level is rejected`() {
        assertTrue(loadFails("LOG_LEVEL" to "VERBOSE").contains("LOG_LEVEL"))
    }

    @Test
    fun `low entropy jwt secret is rejected`() {
        assertTrue(loadFails("JWT_SECRET" to "a".repeat(40)).contains("JWT_SECRET"))
    }

    @Test
    fun `prod requires trusted proxies and forbids debug logs`() {
        assertTrue(loadFails("APP_ENV" to "prod").contains("TRUSTED_PROXIES"))
        val debug = loadFails(
            "APP_ENV" to "prod",
            "TRUSTED_PROXIES" to "127.0.0.1",
            "LOG_LEVEL" to "DEBUG",
        )
        assertTrue(debug.contains("LOG_LEVEL"))
    }

    @Test
    fun `invalid trusted proxy entry is rejected`() {
        assertTrue(loadFails("TRUSTED_PROXIES" to "proxy.internal").contains("TRUSTED_PROXIES"))
        assertTrue(loadFails("TRUSTED_PROXIES" to "10.0.0.0/40").contains("TRUSTED_PROXIES"))
    }

    @Test
    fun `config toString does not leak secrets`() {
        val config = ConfigLoader.load(env())
        assertTrue(config.jwt.toString().contains("secret=***"))
        assertTrue(config.database.toString().contains("password=***"))
        assertTrue(!config.jwt.toString().contains(config.jwt.secret))
        assertTrue(!config.database.toString().contains(config.database.password))
    }

    @Test
    fun `firebase provider requires project id`() {
        assertTrue(loadFails("AUTH_PROVIDER" to "firebase").contains("FIREBASE_PROJECT_ID"))
        assertTrue(loadFails("AUTH_PROVIDER" to "ldap").contains("AUTH_PROVIDER"))
    }

    @Test
    fun `prod config with explicit proxy and info logs loads`() {
        val config = ConfigLoader.load(
            env(
                "APP_ENV" to "prod",
                "TRUSTED_PROXIES" to "127.0.0.1,10.0.0.0/8",
            ),
        )
        assertEquals(AppEnvironment.PROD, config.environment)
        assertEquals(2, config.http.trustedProxies.size)
    }
}
