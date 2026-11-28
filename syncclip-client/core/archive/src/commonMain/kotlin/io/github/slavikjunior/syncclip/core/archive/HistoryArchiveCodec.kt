package io.github.slavikjunior.syncclip.core.archive

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Кодирование и разбор файла архива истории (US-018, US-024).
 *
 * JSON — структура из спецификации (`version`, `exported_at`, `entries`);
 * CSV — RFC 4180 с заголовком `id,text,timestamp,device_name,is_pinned,is_favorite`.
 * Текст может содержать запятые, кавычки и переводы строк, поэтому CSV-поля
 * экранируются, а разбор учитывает кавычки (иначе запись с `"Hello, World!"`
 * разваливалась бы на два поля).
 */
public object HistoryArchiveCodec {

    public const val VERSION: String = "1.0"

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    public fun encode(
        format: HistoryArchiveFormat,
        entries: List<HistoryArchiveEntry>,
        exportedAtIso: String,
    ): String = when (format) {
        HistoryArchiveFormat.JSON -> json.encodeToString(
            JsonArchive.serializer(),
            JsonArchive(
                version = VERSION,
                exportedAt = exportedAtIso,
                entries = entries.map { it.toJson() },
            ),
        )

        HistoryArchiveFormat.CSV -> encodeCsv(entries)
    }

    public fun decode(format: HistoryArchiveFormat, content: String): Result<List<HistoryArchiveEntry>> =
        when (format) {
            HistoryArchiveFormat.JSON -> decodeJson(content)
            HistoryArchiveFormat.CSV -> decodeCsv(content)
        }.mapCatching { entries ->
            if (entries.isEmpty()) throw ArchiveFormatException(ArchiveError.EMPTY)
            entries
        }

    /** Ошибка разбора с машинной причиной: UI решает, что показать. */
    public class ArchiveFormatException(public val error: ArchiveError) : Exception()

    private fun decodeJson(content: String): Result<List<HistoryArchiveEntry>> = runCatching {
        val archive = json.decodeFromString(JsonArchive.serializer(), content)
        if (archive.version != VERSION) throw ArchiveFormatException(ArchiveError.INVALID_FORMAT)
        archive.entries.map { entry ->
            HistoryArchiveEntry(
                id = entry.id,
                text = entry.text,
                timestampMillis = entry.timestamp,
                deviceName = entry.deviceName,
                isPinned = entry.isPinned,
                isFavorite = entry.isFavorite,
            )
        }
    }.recoverCatching { throw asFormatError(it) }

    private fun encodeCsv(entries: List<HistoryArchiveEntry>): String = buildString {
        append(CSV_HEADER).append('\n')
        entries.forEach { entry ->
            appendCsvField(entry.id)
            append(',')
            appendCsvField(entry.text)
            append(',')
            append(entry.timestampMillis.toString())
            append(',')
            appendCsvField(entry.deviceName.orEmpty())
            append(',')
            append(entry.isPinned.toString())
            append(',')
            append(entry.isFavorite.toString())
            append('\n')
        }
    }

    private fun decodeCsv(content: String): Result<List<HistoryArchiveEntry>> = runCatching {
        val rows = parseCsv(content)
        if (rows.isEmpty()) throw ArchiveFormatException(ArchiveError.EMPTY)
        val header = rows.first().map { it.trim().lowercase() }
        if (header != CSV_COLUMNS) throw ArchiveFormatException(ArchiveError.INVALID_FORMAT)
        rows.drop(1)
            // Пустые строки в конце файла — не ошибка (Excel добавляет перевод строки).
            .filterNot { row -> row.size == 1 && row.first().isBlank() }
            .map { row ->
                if (row.size != CSV_COLUMNS.size) {
                    throw ArchiveFormatException(ArchiveError.INVALID_FORMAT)
                }
                HistoryArchiveEntry(
                    id = row[0],
                    text = row[1],
                    timestampMillis = row[2].toLongOrNull()
                        ?: throw ArchiveFormatException(ArchiveError.INVALID_FORMAT),
                    deviceName = row[3].ifBlank { null },
                    isPinned = row[4].toFlag(),
                    isFavorite = row[5].toFlag(),
                )
            }
    }.recoverCatching { throw asFormatError(it) }

    private fun String.toFlag(): Boolean = when (trim().lowercase()) {
        "true", "1" -> true
        "false", "0", "" -> false
        else -> throw ArchiveFormatException(ArchiveError.INVALID_FORMAT)
    }

    private fun asFormatError(error: Throwable): Throwable = when (error) {
        is ArchiveFormatException -> error
        // Любая ошибка сериализации/индексации — «неверный формат», а не краш.
        else -> ArchiveFormatException(ArchiveError.INVALID_FORMAT)
    }

    private fun StringBuilder.appendCsvField(value: String) {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuotes) {
            append(value)
            return
        }
        append('"')
        value.forEach { char ->
            if (char == '"') append("\"\"") else append(char)
        }
        append('"')
    }

    /** Разбор CSV с кавычками: строка — список полей, кавычка внутри поля удваивается. */
    private fun parseCsv(content: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < content.length) {
            val char = content[index]
            when {
                quoted && char == '"' && content.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }

                char == '"' -> quoted = !quoted
                !quoted && char == ',' -> {
                    row.add(field.toString())
                    field.clear()
                }

                !quoted && (char == '\n' || char == '\r') -> {
                    // CRLF считаем одним переводом строки.
                    if (char == '\r' && content.getOrNull(index + 1) == '\n') index++
                    row.add(field.toString())
                    field.clear()
                    rows.add(row)
                    row = mutableListOf()
                }

                else -> field.append(char)
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }

    private fun HistoryArchiveEntry.toJson(): JsonEntry = JsonEntry(
        id = id,
        text = text,
        timestamp = timestampMillis,
        deviceName = deviceName,
        isPinned = isPinned,
        isFavorite = isFavorite,
    )

    @Serializable
    private data class JsonArchive(
        val version: String,
        @kotlinx.serialization.SerialName("exported_at") val exportedAt: String,
        val entries: List<JsonEntry>,
    )

    @Serializable
    private data class JsonEntry(
        val id: String,
        val text: String,
        val timestamp: Long,
        @kotlinx.serialization.SerialName("device_name") val deviceName: String? = null,
        @kotlinx.serialization.SerialName("is_pinned") val isPinned: Boolean = false,
        @kotlinx.serialization.SerialName("is_favorite") val isFavorite: Boolean = false,
    )

    private const val CSV_HEADER = "id,text,timestamp,device_name,is_pinned,is_favorite"

    private val CSV_COLUMNS = CSV_HEADER.split(',')
}
