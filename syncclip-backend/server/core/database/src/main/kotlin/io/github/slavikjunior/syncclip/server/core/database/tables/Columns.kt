package io.github.slavikjunior.syncclip.server.core.database.tables

import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.core.Table
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Общие преобразования времени: в БД TIMESTAMPTZ, в коде — Instant (UTC). */
internal fun Table.timestampUtc(name: String) = timestampWithTimeZone(name)

internal fun Instant.toOffsetDateTime(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

internal fun OffsetDateTime.toInstantUtc(): Instant = toInstant()
