package io.github.slavikjunior.syncclip.server.core.database.tables

import org.jetbrains.exposed.v1.core.ColumnType
import java.net.InetAddress

/**
 * Колонка PostgreSQL `inet` (LOW-13).
 *
 * Значение в коде — каноническая строка IP (её проверяет `Validators.ipOrNull`).
 * Без этого типа Exposed описывал `inet` как `text`, и любая запись шла raw SQL
 * с `CAST(? AS inet)` — хрупко при смене драйвера/Exposed.
 */
internal object InetColumnType : ColumnType<String>() {

    override fun sqlType(): String = "inet"

    override fun valueFromDB(value: Any): String = when (value) {
        is String -> canonical(value)
        is InetAddress -> value.hostAddress
        is org.postgresql.util.PGobject -> canonical(value.value ?: value.toString())
        else -> canonical(value.toString())
    }

    /** PostgreSQL печатает host-адрес как `a.b.c.d/32`; API отдаёт адрес без маски. */
    private fun canonical(text: String): String = when {
        text.endsWith("/32") && !text.contains(':') -> text.removeSuffix("/32")
        text.endsWith("/128") && text.contains(':') -> text.removeSuffix("/128")
        else -> text
    }

    override fun notNullValueToDB(value: String): Any =
        org.postgresql.util.PGobject().apply {
            type = "inet"
            this.value = value
        }

    override fun nonNullValueToString(value: String): String = "'$value'"
}
