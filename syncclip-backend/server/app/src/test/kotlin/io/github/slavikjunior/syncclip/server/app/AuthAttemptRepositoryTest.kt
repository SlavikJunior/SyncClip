package io.github.slavikjunior.syncclip.server.app

import io.github.slavikjunior.syncclip.server.core.database.DatabaseFactory
import io.github.slavikjunior.syncclip.server.core.database.model.AttemptKind
import io.github.slavikjunior.syncclip.server.core.database.repository.Repositories.insertAuthAttempt
import io.github.slavikjunior.syncclip.server.core.database.tx
import io.github.slavikjunior.syncclip.server.contracts.util.UuidV7
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** LOW-13: `inet` пишется/читается типизированной колонкой, включая NULL. */
class AuthAttemptRepositoryTest {

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
    fun `inserts inet and null addresses`() {
        kotlinx.coroutines.runBlocking {
            tx(database.database) {
                insertAuthAttempt(
                    id = UuidV7.newString(),
                    userId = null,
                    deviceId = null,
                    emailHash = "a".repeat(64),
                    ipAddress = "192.168.1.10",
                    userAgent = "test",
                    deviceModel = "test",
                    kind = AttemptKind.LOGIN,
                    success = false,
                )
                insertAuthAttempt(
                    id = UuidV7.newString(),
                    userId = null,
                    deviceId = null,
                    emailHash = "b".repeat(64),
                    ipAddress = null,
                    userAgent = null,
                    deviceModel = null,
                    kind = AttemptKind.LOGIN,
                    success = false,
                )
            }
        }

        val addresses = database.dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT ip_address::text FROM auth_attempts ORDER BY attempted_at").use { rs ->
                    buildList { while (rs.next()) add(rs.getString(1)) }
                }
            }
        }
        assertEquals(listOf("192.168.1.10/32", null), addresses)
    }
}
