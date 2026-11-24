package io.github.slavikjunior.syncclip.server.core.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** LOW-15: SQL-скрипт должен резаться по statement'ам без разрыва литералов. */
class SqlScriptTest {

    @Test
    fun `splits plain statements`() {
        val statements = SqlScript.split("CREATE TABLE a (id int); CREATE INDEX a_idx ON a (id);")
        assertEquals(2, statements.size)
        assertTrue(statements[0].startsWith("CREATE TABLE"))
        assertTrue(statements[1].startsWith("CREATE INDEX"))
    }

    @Test
    fun `keeps semicolons inside strings and identifiers`() {
        val statements = SqlScript.split(
            """
            INSERT INTO t VALUES ('a;b', "weird;name");
            SELECT 1;
            """.trimIndent(),
        )
        assertEquals(2, statements.size)
        assertTrue(statements[0].contains("'a;b'"))
    }

    @Test
    fun `keeps dollar quoted function body intact`() {
        val statements = SqlScript.split(
            """
            CREATE OR REPLACE FUNCTION f() RETURNS TRIGGER AS $$
            BEGIN
                NEW.updated_at := now();
                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;
            CREATE TRIGGER t BEFORE UPDATE ON x FOR EACH ROW EXECUTE FUNCTION f();
            """.trimIndent(),
        )
        assertEquals(2, statements.size)
        assertTrue(statements[0].contains("END;"))
        assertTrue(statements[1].startsWith("CREATE TRIGGER"))
    }

    @Test
    fun `ignores comments and empty statements`() {
        val statements = SqlScript.split(
            """
            -- leading comment
            /* block ; comment */
            ;
            SELECT 1;
            """.trimIndent(),
        )
        assertEquals(1, statements.size)
    }

    @Test
    fun `production migration splits into executable statements`() {
        val sql = javaClass.classLoader.getResourceAsStream("db/migration/V1__init.sql")!!
            .use { it.readBytes().toString(Charsets.UTF_8) }
        val statements = SqlScript.split(sql)
        assertTrue(statements.size > 25, "ожидалось много statement'ов, получено ${statements.size}")
        // BEGIN/COMMIT присутствуют в файле, но раннер их отфильтровывает.
        assertEquals(2, statements.count(SqlScript::isTransactionControl))
    }

    @Test
    fun `transaction control with leading comments is recognized`() {
        assertTrue(SqlScript.isTransactionControl("-- header\nBEGIN;"))
        assertTrue(SqlScript.isTransactionControl("/* x */ COMMIT;"))
        assertFalse(SqlScript.isTransactionControl("SELECT 1"))
    }
}
