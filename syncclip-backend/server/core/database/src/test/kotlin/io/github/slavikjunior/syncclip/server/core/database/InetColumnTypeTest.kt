package io.github.slavikjunior.syncclip.server.core.database

import io.github.slavikjunior.syncclip.server.core.database.tables.InetColumnType
import org.postgresql.util.PGobject
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals

/** LOW-13: канонизация `inet` (PostgreSQL печатает host-адрес с /32 или /128). */
class InetColumnTypeTest {

    @Test
    fun `strips host mask from database values`() {
        assertEquals("192.168.1.10", InetColumnType.valueFromDB("192.168.1.10/32"))
        assertEquals("2001:db8:1:0:0:0:0:5", InetColumnType.valueFromDB("2001:db8:1:0:0:0:0:5/128"))
        assertEquals("10.0.0.0/8", InetColumnType.valueFromDB("10.0.0.0/8"))
    }

    @Test
    fun `accepts pgobject and inet address`() {
        val pg = PGobject().apply {
            type = "inet"
            value = "127.0.0.1/32"
        }
        assertEquals("127.0.0.1", InetColumnType.valueFromDB(pg))
        assertEquals("127.0.0.1", InetColumnType.valueFromDB(InetAddress.getByName("127.0.0.1")))
    }

    @Test
    fun `binds values as typed inet object`() {
        val bound = InetColumnType.notNullValueToDB("192.168.1.10") as PGobject
        assertEquals("inet", bound.type)
        assertEquals("192.168.1.10", bound.value)
        assertEquals("inet", InetColumnType.sqlType())
    }
}
