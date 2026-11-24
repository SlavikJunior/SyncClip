package io.github.slavikjunior.syncclip.server.core.database

/**
 * Делит SQL-скрипт на отдельные statement'ы, не разрывая:
 * строковые литералы (`'...'`, `''`), кавычки идентификаторов (`"..."`),
 * dollar-quoted блоки (`$$ ... $$`, `$tag$ ... $tag$`) и комментарии
 * `--` / `/* ... */`. Нужен для диагностики миграций и корректной работы с
 * `CREATE INDEX CONCURRENTLY` / `preferQueryMode=simple` (LOW-15).
 */
internal object SqlScript {

    fun split(script: String): List<String> {
        val statements = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0

        fun flush() {
            val text = current.toString().trim()
            if (stripComments(text).isNotBlank()) statements += text
            current.setLength(0)
        }

        while (index < script.length) {
            val char = script[index]
            when {
                char == '-' && script.startsWith("--", index) -> {
                    val end = script.indexOf('\n', index).let { if (it < 0) script.length else it }
                    current.append(script, index, end)
                    index = end
                }

                char == '/' && script.startsWith("/*", index) -> {
                    val end = script.indexOf("*/", index + 2).let { if (it < 0) script.length - 2 else it }
                    current.append(script, index, end + 2)
                    index = end + 2
                }

                char == '\'' -> {
                    val end = readQuoted(script, index, '\'')
                    current.append(script, index, end)
                    index = end
                }

                char == '"' -> {
                    val end = readQuoted(script, index, '"')
                    current.append(script, index, end)
                    index = end
                }

                char == '$' -> {
                    val tag = readDollarTag(script, index)
                    if (tag == null) {
                        current.append(char)
                        index++
                    } else {
                        val end = script.indexOf(tag, index + tag.length)
                        val stop = if (end < 0) script.length else end + tag.length
                        current.append(script, index, stop)
                        index = stop
                    }
                }

                char == ';' -> {
                    flush()
                    index++
                }

                else -> {
                    current.append(char)
                    index++
                }
            }
        }
        flush()
        return statements
    }

    /**
     * `BEGIN`/`COMMIT` не должны исполняться внутри управляемой раннером
     * транзакции: сравнение идёт без комментариев и регистра.
     */
    fun isTransactionControl(statement: String): Boolean {
        val cleaned = stripComments(statement).trim().trimEnd(';').trim().uppercase()
        return cleaned in setOf("BEGIN", "COMMIT", "START TRANSACTION", "ROLLBACK")
    }

    private fun stripComments(text: String): String =
        text.replace(BLOCK_COMMENT, " ")
            .replace(LINE_COMMENT, " ")

    private fun readQuoted(script: String, start: Int, quote: Char): Int {        var index = start + 1
        while (index < script.length) {
            if (script[index] == quote) {
                // Удвоенная кавычка внутри литерала — экранирование, не конец.
                if (index + 1 < script.length && script[index + 1] == quote) {
                    index += 2
                    continue
                }
                return index + 1
            }
            index++
        }
        return script.length
    }

    private fun readDollarTag(script: String, start: Int): String? {
        val close = script.indexOf('$', start + 1)
        if (close < 0) return null
        val name = script.substring(start + 1, close)
        if (name.isNotEmpty() && !name.all { it.isLetterOrDigit() || it == '_' }) return null
        return script.substring(start, close + 1)
    }

    private val BLOCK_COMMENT = Regex("(?s)/\\*.*?\\*/")
    private val LINE_COMMENT = Regex("(?m)--.*$")
}
